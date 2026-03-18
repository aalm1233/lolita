package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/lolita/app/backend/internal/config"
	"github.com/lolita/app/backend/internal/repository"
	syncdto "github.com/lolita/app/backend/internal/sync"
	"golang.org/x/crypto/bcrypt"
)

type envelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

func TestAdminLoginAndUnauthorized(t *testing.T) {
	router := newTestRouter(t)

	badLogin := performJSON(t, router, http.MethodPost, "/api/admin/auth/login", map[string]any{
		"password": "wrong-password",
	}, "")
	if badLogin.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for bad login, got %d", badLogin.Code)
	}

	protected := performJSON(t, router, http.MethodGet, "/api/admin/brands", nil, "")
	if protected.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for protected route without token, got %d", protected.Code)
	}

	token := login(t, router)
	if token == "" {
		t.Fatal("expected token from login")
	}
}

func TestSnapshotAndChangesFlow(t *testing.T) {
	router := newTestRouter(t)
	token := login(t, router)

	brandID := createResource(t, router, token, "brands", map[string]any{
		"name": "Atelier Pierrot",
	})
	categoryID := createResource(t, router, token, "categories", map[string]any{
		"name":  "JSK",
		"group": "CLOTHING",
	})
	styleID := createResource(t, router, token, "styles", map[string]any{
		"name": "Elegant",
	})
	seasonID := createResource(t, router, token, "seasons", map[string]any{
		"name": "Spring",
	})
	sourceID := createResource(t, router, token, "sources", map[string]any{
		"name": "Official",
	})
	catalogID := createResource(t, router, token, "catalog-entries", map[string]any{
		"name":             "Rosier JSK",
		"brandPublicId":    brandID,
		"categoryPublicId": categoryID,
		"stylePublicId":    styleID,
		"seasonPublicId":   seasonID,
		"sourcePublicId":   sourceID,
		"seriesName":       "Rosier",
		"referenceUrl":     "https://example.com/catalog/rosier-jsk",
		"imageUrls":        []string{"/uploads/images/catalog-1.jpg"},
		"colors":           []string{"pink"},
		"size":             "M",
		"description":      "Catalog entry",
	})
	coordinateID := createResource(t, router, token, "coordinates", map[string]any{
		"name":        "Rosier Coord",
		"description": "Shared coordinate",
		"imageUrls":   []string{"/uploads/images/coord-1.jpg"},
	})
	itemID := createResource(t, router, token, "items", map[string]any{
		"name":                 "Rosier JSK Shared",
		"description":          "Shared item",
		"brandPublicId":        brandID,
		"categoryPublicId":     categoryID,
		"stylePublicId":        styleID,
		"seasonPublicId":       seasonID,
		"sourcePublicId":       sourceID,
		"catalogEntryPublicId": catalogID,
		"coordinatePublicId":   coordinateID,
		"coordinateOrder":      1,
		"imageUrls":            []string{"/uploads/images/item-1.jpg"},
		"colors":               []string{"pink"},
		"size":                 "M",
		"sizeChartImageUrl":    "/uploads/images/size-chart.jpg",
	})
	pricePlanID := createResource(t, router, token, "price-plans", map[string]any{
		"sharedItemPublicId": itemID,
		"priceType":          "DEPOSIT_BALANCE",
		"totalPrice":         1200.0,
		"deposit":            300.0,
		"balance":            900.0,
		"depositDueAt":       int64(1710000000000),
		"balanceDueAt":       int64(1720000000000),
	})

	for _, resource := range []struct {
		name string
		id   string
	}{
		{"brands", brandID},
		{"categories", categoryID},
		{"styles", styleID},
		{"seasons", seasonID},
		{"sources", sourceID},
		{"catalog-entries", catalogID},
		{"coordinates", coordinateID},
		{"items", itemID},
		{"price-plans", pricePlanID},
	} {
		publishResource(t, router, token, resource.name, resource.id)
	}

	snapshotRec := performJSON(t, router, http.MethodGet, "/api/v1/sync/snapshot", nil, "")
	if snapshotRec.Code != http.StatusOK {
		t.Fatalf("expected snapshot 200, got %d", snapshotRec.Code)
	}

	snapshot := decodeData[syncdto.SnapshotResponse](t, snapshotRec)
	if len(snapshot.Data.Brands) != 1 || len(snapshot.Data.Items) != 1 || len(snapshot.Data.PricePlans) != 1 {
		t.Fatalf("unexpected snapshot counts: %+v", snapshot.Data)
	}
	if snapshot.NextCursor == 0 {
		t.Fatal("expected snapshot cursor to advance")
	}

	updateRec := performJSON(t, router, http.MethodPut, "/api/admin/brands/"+brandID, map[string]any{
		"name": "Atelier Pierrot Updated",
	}, token)
	if updateRec.Code != http.StatusOK {
		t.Fatalf("expected brand update 200, got %d", updateRec.Code)
	}

	deleteRec := performJSON(t, router, http.MethodDelete, "/api/admin/items/"+itemID, nil, token)
	if deleteRec.Code != http.StatusOK {
		t.Fatalf("expected item delete 200, got %d", deleteRec.Code)
	}

	changesRec := performJSON(t, router, http.MethodGet, fmt.Sprintf("/api/v1/sync/changes?cursor=%d", snapshot.NextCursor), nil, "")
	if changesRec.Code != http.StatusOK {
		t.Fatalf("expected changes 200, got %d", changesRec.Code)
	}

	changes := decodeData[syncdto.ChangesResponse](t, changesRec)
	if changes.NextCursor <= snapshot.NextCursor {
		t.Fatalf("expected next cursor > %d, got %d", snapshot.NextCursor, changes.NextCursor)
	}
	if len(changes.Changes.Brands.Upserts) != 1 {
		t.Fatalf("expected one brand upsert, got %d", len(changes.Changes.Brands.Upserts))
	}
	if changes.Changes.Brands.Upserts[0].Name != "Atelier Pierrot Updated" {
		t.Fatalf("expected updated brand name, got %q", changes.Changes.Brands.Upserts[0].Name)
	}
	assertContains(t, changes.Changes.Items.DeletedPublicIDs, itemID)
	assertContains(t, changes.Changes.PricePlans.DeletedPublicIDs, pricePlanID)
}

func TestDependencyGuardAndUploadValidation(t *testing.T) {
	router := newTestRouter(t)
	token := login(t, router)

	brandID := createResource(t, router, token, "brands", map[string]any{
		"name": "Baby",
	})
	categoryID := createResource(t, router, token, "categories", map[string]any{
		"name":  "OP",
		"group": "CLOTHING",
	})
	itemID := createResource(t, router, token, "items", map[string]any{
		"name":             "Baby OP Shared",
		"description":      "Shared item",
		"brandPublicId":    brandID,
		"categoryPublicId": categoryID,
	})

	publishResource(t, router, token, "brands", brandID)
	publishResource(t, router, token, "categories", categoryID)
	publishResource(t, router, token, "items", itemID)

	unpublishRec := performJSON(t, router, http.MethodPost, "/api/admin/brands/"+brandID+"/unpublish", nil, token)
	if unpublishRec.Code != http.StatusConflict {
		t.Fatalf("expected 409 when unpublishing referenced brand, got %d", unpublishRec.Code)
	}

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile("file", "notes.txt")
	if err != nil {
		t.Fatalf("create multipart part: %v", err)
	}
	if _, err := part.Write([]byte("not an image")); err != nil {
		t.Fatalf("write multipart body: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}

	request := httptest.NewRequest(http.MethodPost, "/api/admin/uploads/images", &body)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	request.Header.Set("Authorization", "Bearer "+token)

	recorder := httptest.NewRecorder()
	router.ServeHTTP(recorder, request)
	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for invalid upload type, got %d", recorder.Code)
	}
}

func newTestRouter(t *testing.T) http.Handler {
	t.Helper()

	tempDir := t.TempDir()
	passwordHash, err := bcrypt.GenerateFromPassword([]byte("secret123"), bcrypt.DefaultCost)
	if err != nil {
		t.Fatalf("generate password hash: %v", err)
	}

	cfg := config.Config{
		Server: config.ServerConfig{
			Addr:          ":0",
			Mode:          "test",
			PublicBaseURL: "http://example.test",
		},
		Database: config.DatabaseConfig{
			Path: filepath.Join(tempDir, "backend.db"),
		},
		Auth: config.AuthConfig{
			JWTSecret:         "test-secret",
			AdminPasswordHash: string(passwordHash),
			TokenTTLHours:     24,
		},
		Upload: config.UploadConfig{
			Path:         filepath.Join(tempDir, "uploads", "images"),
			MaxSizeBytes: 1024 * 1024,
			AllowedTypes: []string{"jpg", "jpeg", "png", "webp"},
		},
	}

	if err := cfg.EnsureRuntimePaths(); err != nil {
		t.Fatalf("ensure runtime paths: %v", err)
	}
	store, err := repository.New(cfg)
	if err != nil {
		t.Fatalf("create store: %v", err)
	}
	t.Cleanup(func() {
		_ = store.Close()
	})
	if err := store.AutoMigrate(); err != nil {
		t.Fatalf("automigrate: %v", err)
	}

	return NewRouter(cfg, store)
}

func login(t *testing.T, router http.Handler) string {
	t.Helper()

	recorder := performJSON(t, router, http.MethodPost, "/api/admin/auth/login", map[string]any{
		"password": "secret123",
	}, "")
	if recorder.Code != http.StatusOK {
		t.Fatalf("expected successful login, got %d", recorder.Code)
	}

	var data struct {
		Token string `json:"token"`
	}
	decodeInto(t, decodeEnvelope(t, recorder).Data, &data)
	return data.Token
}

func createResource(t *testing.T, router http.Handler, token string, resource string, payload any) string {
	t.Helper()

	recorder := performJSON(t, router, http.MethodPost, "/api/admin/"+resource, payload, token)
	if recorder.Code != http.StatusCreated {
		t.Fatalf("expected create %s 201, got %d: %s", resource, recorder.Code, recorder.Body.String())
	}

	var data struct {
		PublicID string `json:"publicId"`
	}
	decodeInto(t, decodeEnvelope(t, recorder).Data, &data)
	if data.PublicID == "" {
		t.Fatalf("expected publicId from %s create response", resource)
	}
	return data.PublicID
}

func publishResource(t *testing.T, router http.Handler, token string, resource string, publicID string) {
	t.Helper()

	recorder := performJSON(t, router, http.MethodPost, "/api/admin/"+resource+"/"+publicID+"/publish", nil, token)
	if recorder.Code != http.StatusOK {
		t.Fatalf("expected publish %s 200, got %d: %s", resource, recorder.Code, recorder.Body.String())
	}
}

func performJSON(t *testing.T, router http.Handler, method string, path string, payload any, token string) *httptest.ResponseRecorder {
	t.Helper()

	var body bytes.Buffer
	if payload != nil {
		if err := json.NewEncoder(&body).Encode(payload); err != nil {
			t.Fatalf("encode payload: %v", err)
		}
	}

	request := httptest.NewRequest(method, path, &body)
	if payload != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}

	recorder := httptest.NewRecorder()
	router.ServeHTTP(recorder, request)
	return recorder
}

func decodeEnvelope(t *testing.T, recorder *httptest.ResponseRecorder) envelope {
	t.Helper()

	var response envelope
	if err := json.Unmarshal(recorder.Body.Bytes(), &response); err != nil {
		t.Fatalf("decode envelope: %v", err)
	}
	return response
}

func decodeData[T any](t *testing.T, recorder *httptest.ResponseRecorder) T {
	t.Helper()

	var value T
	decodeInto(t, decodeEnvelope(t, recorder).Data, &value)
	return value
}

func decodeInto(t *testing.T, raw json.RawMessage, target any) {
	t.Helper()

	if len(raw) == 0 {
		t.Fatal("expected response data")
	}
	if err := json.Unmarshal(raw, target); err != nil {
		t.Fatalf("decode response data: %v", err)
	}
}

func assertContains(t *testing.T, values []string, expected string) {
	t.Helper()

	for _, value := range values {
		if value == expected {
			return
		}
	}
	t.Fatalf("expected %q in %v", expected, values)
}

func TestMain(m *testing.M) {
	code := m.Run()
	os.Exit(code)
}
