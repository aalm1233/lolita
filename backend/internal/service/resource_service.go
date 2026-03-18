package service

import (
	"errors"
	"math"
	"strings"

	"github.com/lolita/app/backend/internal/model"
	"github.com/lolita/app/backend/internal/repository"
	syncdto "github.com/lolita/app/backend/internal/sync"
	"gorm.io/gorm"
)

const (
	priceTypeFull           = "FULL"
	priceTypeDepositBalance = "DEPOSIT_BALANCE"
)

type ResourceService struct {
	store *repository.Store
}

type BrandUpsertInput struct {
	Name    string  `json:"name" binding:"required"`
	LogoURL *string `json:"logoUrl"`
}

type CategoryUpsertInput struct {
	Name  string `json:"name" binding:"required"`
	Group string `json:"group" binding:"required"`
}

type StyleUpsertInput struct {
	Name string `json:"name" binding:"required"`
}

type SeasonUpsertInput struct {
	Name string `json:"name" binding:"required"`
}

type SourceUpsertInput struct {
	Name string `json:"name" binding:"required"`
}

type CatalogEntryUpsertInput struct {
	Name             string   `json:"name" binding:"required"`
	BrandPublicID    *string  `json:"brandPublicId"`
	CategoryPublicID *string  `json:"categoryPublicId"`
	StylePublicID    *string  `json:"stylePublicId"`
	SeasonPublicID   *string  `json:"seasonPublicId"`
	SourcePublicID   *string  `json:"sourcePublicId"`
	SeriesName       *string  `json:"seriesName"`
	ReferenceURL     *string  `json:"referenceUrl"`
	ImageURLs        []string `json:"imageUrls"`
	Colors           []string `json:"colors"`
	Size             *string  `json:"size"`
	Description      string   `json:"description"`
}

type SharedItemUpsertInput struct {
	Name                 string   `json:"name" binding:"required"`
	Description          string   `json:"description"`
	BrandPublicID        string   `json:"brandPublicId" binding:"required"`
	CategoryPublicID     string   `json:"categoryPublicId" binding:"required"`
	StylePublicID        *string  `json:"stylePublicId"`
	SeasonPublicID       *string  `json:"seasonPublicId"`
	SourcePublicID       *string  `json:"sourcePublicId"`
	CatalogEntryPublicID *string  `json:"catalogEntryPublicId"`
	CoordinatePublicID   *string  `json:"coordinatePublicId"`
	CoordinateOrder      int      `json:"coordinateOrder"`
	ImageURLs            []string `json:"imageUrls"`
	Colors               []string `json:"colors"`
	Size                 *string  `json:"size"`
	SizeChartImageURL    *string  `json:"sizeChartImageUrl"`
}

type CoordinateUpsertInput struct {
	Name        string   `json:"name" binding:"required"`
	Description string   `json:"description"`
	ImageURLs   []string `json:"imageUrls"`
}

type PricePlanUpsertInput struct {
	SharedItemPublicID string   `json:"sharedItemPublicId" binding:"required"`
	PriceType          string   `json:"priceType" binding:"required"`
	TotalPrice         float64  `json:"totalPrice" binding:"required"`
	Deposit            *float64 `json:"deposit"`
	Balance            *float64 `json:"balance"`
	DepositDueAt       *int64   `json:"depositDueAt"`
	BalanceDueAt       *int64   `json:"balanceDueAt"`
}

func NewResourceService(store *repository.Store) *ResourceService {
	return &ResourceService{store: store}
}

func (s *ResourceService) List(resource model.ResourceType) (any, error) {
	db := s.store.DB()

	switch resource {
	case model.ResourceBrands:
		var items []model.Brand
		if err := repository.Active(db).Order("name ASC").Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminBrandDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminBrandFromModel(item))
		}
		return response, nil
	case model.ResourceCategories:
		var items []model.Category
		if err := repository.Active(db).Order("name ASC").Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminCategoryDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminCategoryFromModel(item))
		}
		return response, nil
	case model.ResourceStyles:
		var items []model.Style
		if err := repository.Active(db).Order("name ASC").Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminStyleDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminStyleFromModel(item))
		}
		return response, nil
	case model.ResourceSeasons:
		var items []model.Season
		if err := repository.Active(db).Order("name ASC").Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminSeasonDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminSeasonFromModel(item))
		}
		return response, nil
	case model.ResourceSources:
		var items []model.Source
		if err := repository.Active(db).Order("name ASC").Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminSourceDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminSourceFromModel(item))
		}
		return response, nil
	case model.ResourceCatalogEntries:
		var items []model.CatalogEntry
		if err := s.preloadCatalogEntries(repository.Active(db).Order("updated_at DESC")).Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminCatalogEntryDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminCatalogEntryFromModel(item))
		}
		return response, nil
	case model.ResourceItems:
		var items []model.SharedItem
		if err := s.preloadSharedItems(repository.Active(db).Order("updated_at DESC")).Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminSharedItemDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminSharedItemFromModel(item))
		}
		return response, nil
	case model.ResourceCoordinates:
		var items []model.Coordinate
		if err := repository.Active(db).Order("updated_at DESC").Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminCoordinateDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminCoordinateFromModel(item))
		}
		return response, nil
	case model.ResourcePricePlans:
		var items []model.PricePlan
		if err := s.preloadPricePlans(repository.Active(db).Order("updated_at DESC")).Find(&items).Error; err != nil {
			return nil, err
		}
		response := make([]syncdto.AdminPricePlanDTO, 0, len(items))
		for _, item := range items {
			response = append(response, syncdto.AdminPricePlanFromModel(item))
		}
		return response, nil
	default:
		return nil, BadRequest("unsupported resource type")
	}
}

func (s *ResourceService) Get(resource model.ResourceType, publicID string) (any, error) {
	return s.getResource(resource, strings.TrimSpace(publicID))
}

func (s *ResourceService) Create(resource model.ResourceType, payload any) (any, error) {
	var response any

	err := s.store.WithTx(func(tx *gorm.DB) error {
		var innerErr error
		switch resource {
		case model.ResourceBrands:
			response, innerErr = s.createBrand(tx, payload.(BrandUpsertInput))
		case model.ResourceCategories:
			response, innerErr = s.createCategory(tx, payload.(CategoryUpsertInput))
		case model.ResourceStyles:
			response, innerErr = s.createStyle(tx, payload.(StyleUpsertInput))
		case model.ResourceSeasons:
			response, innerErr = s.createSeason(tx, payload.(SeasonUpsertInput))
		case model.ResourceSources:
			response, innerErr = s.createSource(tx, payload.(SourceUpsertInput))
		case model.ResourceCatalogEntries:
			response, innerErr = s.createCatalogEntry(tx, payload.(CatalogEntryUpsertInput))
		case model.ResourceItems:
			response, innerErr = s.createSharedItem(tx, payload.(SharedItemUpsertInput))
		case model.ResourceCoordinates:
			response, innerErr = s.createCoordinate(tx, payload.(CoordinateUpsertInput))
		case model.ResourcePricePlans:
			response, innerErr = s.createPricePlan(tx, payload.(PricePlanUpsertInput))
		default:
			innerErr = BadRequest("unsupported resource type")
		}
		return innerErr
	})

	return response, err
}

func (s *ResourceService) Update(resource model.ResourceType, publicID string, payload any) (any, error) {
	var response any

	err := s.store.WithTx(func(tx *gorm.DB) error {
		var innerErr error
		switch resource {
		case model.ResourceBrands:
			response, innerErr = s.updateBrand(tx, publicID, payload.(BrandUpsertInput))
		case model.ResourceCategories:
			response, innerErr = s.updateCategory(tx, publicID, payload.(CategoryUpsertInput))
		case model.ResourceStyles:
			response, innerErr = s.updateStyle(tx, publicID, payload.(StyleUpsertInput))
		case model.ResourceSeasons:
			response, innerErr = s.updateSeason(tx, publicID, payload.(SeasonUpsertInput))
		case model.ResourceSources:
			response, innerErr = s.updateSource(tx, publicID, payload.(SourceUpsertInput))
		case model.ResourceCatalogEntries:
			response, innerErr = s.updateCatalogEntry(tx, publicID, payload.(CatalogEntryUpsertInput))
		case model.ResourceItems:
			response, innerErr = s.updateSharedItem(tx, publicID, payload.(SharedItemUpsertInput))
		case model.ResourceCoordinates:
			response, innerErr = s.updateCoordinate(tx, publicID, payload.(CoordinateUpsertInput))
		case model.ResourcePricePlans:
			response, innerErr = s.updatePricePlan(tx, publicID, payload.(PricePlanUpsertInput))
		default:
			innerErr = BadRequest("unsupported resource type")
		}
		return innerErr
	})

	return response, err
}

func (s *ResourceService) Publish(resource model.ResourceType, publicID string) (any, error) {
	var response any

	err := s.store.WithTx(func(tx *gorm.DB) error {
		var innerErr error
		switch resource {
		case model.ResourceBrands:
			response, innerErr = s.publishBrand(tx, publicID)
		case model.ResourceCategories:
			response, innerErr = s.publishCategory(tx, publicID)
		case model.ResourceStyles:
			response, innerErr = s.publishStyle(tx, publicID)
		case model.ResourceSeasons:
			response, innerErr = s.publishSeason(tx, publicID)
		case model.ResourceSources:
			response, innerErr = s.publishSource(tx, publicID)
		case model.ResourceCatalogEntries:
			response, innerErr = s.publishCatalogEntry(tx, publicID)
		case model.ResourceItems:
			response, innerErr = s.publishSharedItem(tx, publicID)
		case model.ResourceCoordinates:
			response, innerErr = s.publishCoordinate(tx, publicID)
		case model.ResourcePricePlans:
			response, innerErr = s.publishPricePlan(tx, publicID)
		default:
			innerErr = BadRequest("unsupported resource type")
		}
		return innerErr
	})

	return response, err
}

func (s *ResourceService) Unpublish(resource model.ResourceType, publicID string) (any, error) {
	var response any

	err := s.store.WithTx(func(tx *gorm.DB) error {
		var innerErr error
		switch resource {
		case model.ResourceBrands:
			response, innerErr = s.unpublishBrand(tx, publicID)
		case model.ResourceCategories:
			response, innerErr = s.unpublishCategory(tx, publicID)
		case model.ResourceStyles:
			response, innerErr = s.unpublishStyle(tx, publicID)
		case model.ResourceSeasons:
			response, innerErr = s.unpublishSeason(tx, publicID)
		case model.ResourceSources:
			response, innerErr = s.unpublishSource(tx, publicID)
		case model.ResourceCatalogEntries:
			response, innerErr = s.unpublishCatalogEntry(tx, publicID)
		case model.ResourceItems:
			response, innerErr = s.unpublishSharedItem(tx, publicID)
		case model.ResourceCoordinates:
			response, innerErr = s.unpublishCoordinate(tx, publicID)
		case model.ResourcePricePlans:
			response, innerErr = s.unpublishPricePlan(tx, publicID)
		default:
			innerErr = BadRequest("unsupported resource type")
		}
		return innerErr
	})

	return response, err
}

func (s *ResourceService) Delete(resource model.ResourceType, publicID string) error {
	return s.store.WithTx(func(tx *gorm.DB) error {
		switch resource {
		case model.ResourceBrands:
			return s.deleteBrand(tx, publicID)
		case model.ResourceCategories:
			return s.deleteCategory(tx, publicID)
		case model.ResourceStyles:
			return s.deleteStyle(tx, publicID)
		case model.ResourceSeasons:
			return s.deleteSeason(tx, publicID)
		case model.ResourceSources:
			return s.deleteSource(tx, publicID)
		case model.ResourceCatalogEntries:
			return s.deleteCatalogEntry(tx, publicID)
		case model.ResourceItems:
			return s.deleteSharedItem(tx, publicID)
		case model.ResourceCoordinates:
			return s.deleteCoordinate(tx, publicID)
		case model.ResourcePricePlans:
			return s.deletePricePlan(tx, publicID)
		default:
			return BadRequest("unsupported resource type")
		}
	})
}

func (s *ResourceService) getResource(resource model.ResourceType, publicID string) (any, error) {
	db := s.store.DB()

	switch resource {
	case model.ResourceBrands:
		item, err := findActiveByPublicID[model.Brand](db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminBrandFromModel(*item), nil
	case model.ResourceCategories:
		item, err := findActiveByPublicID[model.Category](db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminCategoryFromModel(*item), nil
	case model.ResourceStyles:
		item, err := findActiveByPublicID[model.Style](db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminStyleFromModel(*item), nil
	case model.ResourceSeasons:
		item, err := findActiveByPublicID[model.Season](db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminSeasonFromModel(*item), nil
	case model.ResourceSources:
		item, err := findActiveByPublicID[model.Source](db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminSourceFromModel(*item), nil
	case model.ResourceCatalogEntries:
		item, err := s.findCatalogEntry(db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminCatalogEntryFromModel(*item), nil
	case model.ResourceItems:
		item, err := s.findSharedItem(db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminSharedItemFromModel(*item), nil
	case model.ResourceCoordinates:
		item, err := findActiveByPublicID[model.Coordinate](db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminCoordinateFromModel(*item), nil
	case model.ResourcePricePlans:
		item, err := s.findPricePlan(db, publicID)
		if err != nil {
			return nil, err
		}
		return syncdto.AdminPricePlanFromModel(*item), nil
	default:
		return nil, BadRequest("unsupported resource type")
	}
}

func logChange(tx *gorm.DB, resource model.ResourceType, operation model.ChangeOperation, publicID string) error {
	return tx.Create(&model.ChangeEvent{
		ResourceType: resource,
		Operation:    operation,
		PublicID:     publicID,
		ChangedAt:    model.NowMilli(),
	}).Error
}

func findActiveByPublicID[T any](db *gorm.DB, publicID string) (*T, error) {
	var item T
	if err := repository.Active(db).Where("public_id = ?", strings.TrimSpace(publicID)).First(&item).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, NotFound("resource not found")
		}
		return nil, err
	}
	return &item, nil
}

func resolveOptionalReference[T any](db *gorm.DB, publicID *string, missingMessage string) (*uint, *T, error) {
	if publicID == nil || strings.TrimSpace(*publicID) == "" {
		return nil, nil, nil
	}

	item, err := findActiveByPublicID[T](db, strings.TrimSpace(*publicID))
	if err != nil {
		if _, ok := err.(*AppError); ok {
			return nil, nil, BadRequest(missingMessage)
		}
		return nil, nil, err
	}

	base := baseOf(item)
	return &base.ID, item, nil
}

func resolveRequiredReference[T any](db *gorm.DB, publicID *string, missingMessage string) (*uint, *T, error) {
	if publicID == nil || strings.TrimSpace(*publicID) == "" {
		return nil, nil, BadRequest(missingMessage)
	}
	return resolveOptionalReference[T](db, publicID, missingMessage)
}

func baseOf(record any) *model.BaseResource {
	switch typed := record.(type) {
	case *model.Brand:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.Category:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.Style:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.Season:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.Source:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.CatalogEntry:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.SharedItem:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.Coordinate:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	case *model.PricePlan:
		if typed == nil {
			return nil
		}
		return &typed.BaseResource
	default:
		return nil
	}
}

func ensureDependenciesPublished(records ...any) error {
	for _, record := range records {
		if record == nil {
			continue
		}
		base := baseOf(record)
		if base == nil {
			continue
		}
		if base.PublishStatus != model.PublishStatusPublished || base.IsDeleted() {
			return Conflict("all referenced resources must be published before this record can be published")
		}
	}
	return nil
}

func ensureRequiredAssociationPublished(record any, message string) error {
	if record == nil {
		return Conflict(message)
	}

	base := baseOf(record)
	if base == nil || base.PublishStatus != model.PublishStatusPublished || base.IsDeleted() {
		return Conflict(message)
	}
	return nil
}

func ensureUniqueName[T any](db *gorm.DB, name string, excludeID uint) error {
	query := repository.Active(db.Model(new(T))).Where("LOWER(name) = ?", strings.ToLower(strings.TrimSpace(name)))
	if excludeID > 0 {
		query = query.Where("id <> ?", excludeID)
	}

	var count int64
	if err := query.Count(&count).Error; err != nil {
		return err
	}
	if count > 0 {
		return Conflict("name already exists")
	}
	return nil
}

func ensureReferenceCount[T any](db *gorm.DB, column string, id uint, message string) error {
	var count int64
	if err := repository.Active(db.Model(new(T))).Where(column+" = ?", id).Count(&count).Error; err != nil {
		return err
	}
	if count > 0 {
		return Conflict(message)
	}
	return nil
}

func ensurePublishedReferenceCount[T any](db *gorm.DB, column string, id uint, message string) error {
	var count int64
	if err := repository.Published(db.Model(new(T))).Where(column+" = ?", id).Count(&count).Error; err != nil {
		return err
	}
	if count > 0 {
		return Conflict(message)
	}
	return nil
}

func validateName(value string, message string) error {
	if strings.TrimSpace(value) == "" {
		return BadRequest(message)
	}
	return nil
}

func validateCategoryGroup(group string) error {
	switch strings.ToUpper(strings.TrimSpace(group)) {
	case "CLOTHING", "ACCESSORY":
		return nil
	default:
		return BadRequest("category group must be CLOTHING or ACCESSORY")
	}
}

func validatePricePlanInput(input PricePlanUpsertInput) error {
	priceType := strings.ToUpper(strings.TrimSpace(input.PriceType))
	if input.TotalPrice <= 0 {
		return BadRequest("totalPrice must be greater than 0")
	}

	switch priceType {
	case priceTypeFull:
		if input.Deposit != nil || input.Balance != nil || input.DepositDueAt != nil || input.BalanceDueAt != nil {
			return BadRequest("FULL price plans cannot include deposit or balance fields")
		}
	case priceTypeDepositBalance:
		if input.Deposit == nil || input.Balance == nil {
			return BadRequest("DEPOSIT_BALANCE price plans require both deposit and balance")
		}
		if *input.Deposit <= 0 || *input.Balance <= 0 {
			return BadRequest("deposit and balance must both be greater than 0")
		}
		if math.Abs((*input.Deposit+*input.Balance)-input.TotalPrice) > 0.01 {
			return BadRequest("deposit plus balance must equal totalPrice")
		}
	default:
		return BadRequest("priceType must be FULL or DEPOSIT_BALANCE")
	}

	return nil
}

func normalizeOptional(value *string) string {
	if value == nil {
		return ""
	}
	return strings.TrimSpace(*value)
}

func normalizeSlice(values []string) model.StringSlice {
	normalized := make(model.StringSlice, 0, len(values))
	for _, value := range values {
		if trimmed := strings.TrimSpace(value); trimmed != "" {
			normalized = append(normalized, trimmed)
		}
	}
	return normalized
}

func stringPointer(value string) *string {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	return &value
}

func copyFloatPointer(value *float64) *float64 {
	if value == nil {
		return nil
	}
	copied := *value
	return &copied
}

func copyInt64Pointer(value *int64) *int64 {
	if value == nil {
		return nil
	}
	copied := *value
	return &copied
}
