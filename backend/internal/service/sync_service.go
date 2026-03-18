package service

import (
	"errors"
	"sort"
	"strings"

	"github.com/lolita/app/backend/internal/model"
	"github.com/lolita/app/backend/internal/repository"
	syncdto "github.com/lolita/app/backend/internal/sync"
	"gorm.io/gorm"
)

type SyncService struct {
	store     *repository.Store
	resources *ResourceService
}

func NewSyncService(store *repository.Store) *SyncService {
	return &SyncService{
		store:     store,
		resources: NewResourceService(store),
	}
}

func (s *SyncService) Snapshot(assetBaseURL string) (*syncdto.SnapshotResponse, error) {
	db := s.store.DB()

	var brands []model.Brand
	if err := repository.Published(db).Order("name ASC").Find(&brands).Error; err != nil {
		return nil, err
	}

	var categories []model.Category
	if err := repository.Published(db).Order("name ASC").Find(&categories).Error; err != nil {
		return nil, err
	}

	var styles []model.Style
	if err := repository.Published(db).Order("name ASC").Find(&styles).Error; err != nil {
		return nil, err
	}

	var seasons []model.Season
	if err := repository.Published(db).Order("name ASC").Find(&seasons).Error; err != nil {
		return nil, err
	}

	var sources []model.Source
	if err := repository.Published(db).Order("name ASC").Find(&sources).Error; err != nil {
		return nil, err
	}

	var catalogEntries []model.CatalogEntry
	if err := s.resources.preloadCatalogEntries(repository.Published(db).Order("updated_at DESC")).Find(&catalogEntries).Error; err != nil {
		return nil, err
	}

	var sharedItems []model.SharedItem
	if err := s.resources.preloadSharedItems(repository.Published(db).Order("updated_at DESC")).Find(&sharedItems).Error; err != nil {
		return nil, err
	}

	var coordinates []model.Coordinate
	if err := repository.Published(db).Order("updated_at DESC").Find(&coordinates).Error; err != nil {
		return nil, err
	}

	var pricePlans []model.PricePlan
	if err := s.resources.preloadPricePlans(repository.Published(db).Order("updated_at DESC")).Find(&pricePlans).Error; err != nil {
		return nil, err
	}

	nextCursor, err := s.latestCursor()
	if err != nil {
		return nil, err
	}

	response := &syncdto.SnapshotResponse{
		SchemaVersion: syncdto.SchemaVersion,
		AssetBaseURL:  strings.TrimRight(assetBaseURL, "/"),
		NextCursor:    nextCursor,
		Data: syncdto.SnapshotData{
			Brands:         make([]syncdto.BrandDTO, 0, len(brands)),
			Categories:     make([]syncdto.CategoryDTO, 0, len(categories)),
			Styles:         make([]syncdto.StyleDTO, 0, len(styles)),
			Seasons:        make([]syncdto.SeasonDTO, 0, len(seasons)),
			Sources:        make([]syncdto.SourceDTO, 0, len(sources)),
			CatalogEntries: make([]syncdto.CatalogEntryDTO, 0, len(catalogEntries)),
			Items:          make([]syncdto.SharedItemDTO, 0, len(sharedItems)),
			Coordinates:    make([]syncdto.CoordinateDTO, 0, len(coordinates)),
			PricePlans:     make([]syncdto.PricePlanDTO, 0, len(pricePlans)),
		},
	}

	for _, item := range brands {
		response.Data.Brands = append(response.Data.Brands, syncdto.BrandFromModel(item))
	}
	for _, item := range categories {
		response.Data.Categories = append(response.Data.Categories, syncdto.CategoryFromModel(item))
	}
	for _, item := range styles {
		response.Data.Styles = append(response.Data.Styles, syncdto.StyleFromModel(item))
	}
	for _, item := range seasons {
		response.Data.Seasons = append(response.Data.Seasons, syncdto.SeasonFromModel(item))
	}
	for _, item := range sources {
		response.Data.Sources = append(response.Data.Sources, syncdto.SourceFromModel(item))
	}
	for _, item := range catalogEntries {
		response.Data.CatalogEntries = append(response.Data.CatalogEntries, syncdto.CatalogEntryFromModel(item))
	}
	for _, item := range sharedItems {
		response.Data.Items = append(response.Data.Items, syncdto.SharedItemFromModel(item))
	}
	for _, item := range coordinates {
		response.Data.Coordinates = append(response.Data.Coordinates, syncdto.CoordinateFromModel(item))
	}
	for _, item := range pricePlans {
		response.Data.PricePlans = append(response.Data.PricePlans, syncdto.PricePlanFromModel(item))
	}

	return response, nil
}

func (s *SyncService) Changes(assetBaseURL string, cursor uint64, limit int) (*syncdto.ChangesResponse, error) {
	if limit <= 0 {
		limit = 200
	}

	var events []model.ChangeEvent
	if err := s.store.DB().
		Where("cursor > ?", cursor).
		Order("cursor ASC").
		Limit(limit).
		Find(&events).Error; err != nil {
		return nil, err
	}

	response := &syncdto.ChangesResponse{
		SchemaVersion: syncdto.SchemaVersion,
		AssetBaseURL:  strings.TrimRight(assetBaseURL, "/"),
		NextCursor:    cursor,
		Changes:       newEmptyChangeBatch(),
	}
	if len(events) == 0 {
		return response, nil
	}

	latestEvents := dedupeLatestEvents(events)
	for _, event := range latestEvents {
		switch event.Operation {
		case model.ChangeOperationDelete:
			appendDeletedID(&response.Changes, event.ResourceType, event.PublicID)
		case model.ChangeOperationUpsert:
			if err := s.appendUpsert(&response.Changes, event.ResourceType, event.PublicID); err != nil {
				return nil, err
			}
		}
	}

	response.NextCursor = events[len(events)-1].Cursor
	return response, nil
}

func (s *SyncService) appendUpsert(batch *syncdto.ChangeBatch, resource model.ResourceType, publicID string) error {
	db := s.store.DB()

	switch resource {
	case model.ResourceBrands:
		var item model.Brand
		if err := repository.Published(db).Where("public_id = ?", publicID).First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Brands.Upserts = append(batch.Brands.Upserts, syncdto.BrandFromModel(item))
	case model.ResourceCategories:
		var item model.Category
		if err := repository.Published(db).Where("public_id = ?", publicID).First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Categories.Upserts = append(batch.Categories.Upserts, syncdto.CategoryFromModel(item))
	case model.ResourceStyles:
		var item model.Style
		if err := repository.Published(db).Where("public_id = ?", publicID).First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Styles.Upserts = append(batch.Styles.Upserts, syncdto.StyleFromModel(item))
	case model.ResourceSeasons:
		var item model.Season
		if err := repository.Published(db).Where("public_id = ?", publicID).First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Seasons.Upserts = append(batch.Seasons.Upserts, syncdto.SeasonFromModel(item))
	case model.ResourceSources:
		var item model.Source
		if err := repository.Published(db).Where("public_id = ?", publicID).First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Sources.Upserts = append(batch.Sources.Upserts, syncdto.SourceFromModel(item))
	case model.ResourceCatalogEntries:
		var item model.CatalogEntry
		if err := s.resources.preloadCatalogEntries(repository.Published(db)).
			Where("public_id = ?", publicID).
			First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.CatalogEntries.Upserts = append(batch.CatalogEntries.Upserts, syncdto.CatalogEntryFromModel(item))
	case model.ResourceItems:
		var item model.SharedItem
		if err := s.resources.preloadSharedItems(repository.Published(db)).
			Where("public_id = ?", publicID).
			First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Items.Upserts = append(batch.Items.Upserts, syncdto.SharedItemFromModel(item))
	case model.ResourceCoordinates:
		var item model.Coordinate
		if err := repository.Published(db).Where("public_id = ?", publicID).First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.Coordinates.Upserts = append(batch.Coordinates.Upserts, syncdto.CoordinateFromModel(item))
	case model.ResourcePricePlans:
		var item model.PricePlan
		if err := s.resources.preloadPricePlans(repository.Published(db)).
			Where("public_id = ?", publicID).
			First(&item).Error; err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				return nil
			}
			return err
		}
		batch.PricePlans.Upserts = append(batch.PricePlans.Upserts, syncdto.PricePlanFromModel(item))
	default:
		return BadRequest("unsupported resource type")
	}

	return nil
}

func (s *SyncService) latestCursor() (uint64, error) {
	var cursor uint64
	if err := s.store.DB().
		Model(&model.ChangeEvent{}).
		Select("COALESCE(MAX(cursor), 0)").
		Scan(&cursor).Error; err != nil {
		return 0, err
	}
	return cursor, nil
}

func appendDeletedID(batch *syncdto.ChangeBatch, resource model.ResourceType, publicID string) {
	switch resource {
	case model.ResourceBrands:
		batch.Brands.DeletedPublicIDs = append(batch.Brands.DeletedPublicIDs, publicID)
	case model.ResourceCategories:
		batch.Categories.DeletedPublicIDs = append(batch.Categories.DeletedPublicIDs, publicID)
	case model.ResourceStyles:
		batch.Styles.DeletedPublicIDs = append(batch.Styles.DeletedPublicIDs, publicID)
	case model.ResourceSeasons:
		batch.Seasons.DeletedPublicIDs = append(batch.Seasons.DeletedPublicIDs, publicID)
	case model.ResourceSources:
		batch.Sources.DeletedPublicIDs = append(batch.Sources.DeletedPublicIDs, publicID)
	case model.ResourceCatalogEntries:
		batch.CatalogEntries.DeletedPublicIDs = append(batch.CatalogEntries.DeletedPublicIDs, publicID)
	case model.ResourceItems:
		batch.Items.DeletedPublicIDs = append(batch.Items.DeletedPublicIDs, publicID)
	case model.ResourceCoordinates:
		batch.Coordinates.DeletedPublicIDs = append(batch.Coordinates.DeletedPublicIDs, publicID)
	case model.ResourcePricePlans:
		batch.PricePlans.DeletedPublicIDs = append(batch.PricePlans.DeletedPublicIDs, publicID)
	}
}

func newEmptyChangeBatch() syncdto.ChangeBatch {
	return syncdto.ChangeBatch{
		Brands:         syncdto.ResourceChangeSet[syncdto.BrandDTO]{Upserts: []syncdto.BrandDTO{}, DeletedPublicIDs: []string{}},
		Categories:     syncdto.ResourceChangeSet[syncdto.CategoryDTO]{Upserts: []syncdto.CategoryDTO{}, DeletedPublicIDs: []string{}},
		Styles:         syncdto.ResourceChangeSet[syncdto.StyleDTO]{Upserts: []syncdto.StyleDTO{}, DeletedPublicIDs: []string{}},
		Seasons:        syncdto.ResourceChangeSet[syncdto.SeasonDTO]{Upserts: []syncdto.SeasonDTO{}, DeletedPublicIDs: []string{}},
		Sources:        syncdto.ResourceChangeSet[syncdto.SourceDTO]{Upserts: []syncdto.SourceDTO{}, DeletedPublicIDs: []string{}},
		CatalogEntries: syncdto.ResourceChangeSet[syncdto.CatalogEntryDTO]{Upserts: []syncdto.CatalogEntryDTO{}, DeletedPublicIDs: []string{}},
		Items:          syncdto.ResourceChangeSet[syncdto.SharedItemDTO]{Upserts: []syncdto.SharedItemDTO{}, DeletedPublicIDs: []string{}},
		Coordinates:    syncdto.ResourceChangeSet[syncdto.CoordinateDTO]{Upserts: []syncdto.CoordinateDTO{}, DeletedPublicIDs: []string{}},
		PricePlans:     syncdto.ResourceChangeSet[syncdto.PricePlanDTO]{Upserts: []syncdto.PricePlanDTO{}, DeletedPublicIDs: []string{}},
	}
}

func dedupeLatestEvents(events []model.ChangeEvent) []model.ChangeEvent {
	type eventKey struct {
		resource model.ResourceType
		publicID string
	}

	latest := make(map[eventKey]model.ChangeEvent, len(events))
	for _, event := range events {
		key := eventKey{resource: event.ResourceType, publicID: event.PublicID}
		latest[key] = event
	}

	deduped := make([]model.ChangeEvent, 0, len(latest))
	for _, event := range latest {
		deduped = append(deduped, event)
	}
	sort.Slice(deduped, func(i, j int) bool {
		return deduped[i].Cursor < deduped[j].Cursor
	})
	return deduped
}
