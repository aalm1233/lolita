package service

import (
	"errors"
	"strings"

	"github.com/lolita/app/backend/internal/model"
	"github.com/lolita/app/backend/internal/repository"
	syncdto "github.com/lolita/app/backend/internal/sync"
	"gorm.io/gorm"
)

func (s *ResourceService) preloadCatalogEntries(db *gorm.DB) *gorm.DB {
	return db.
		Preload("Brand").
		Preload("Category").
		Preload("Style").
		Preload("Season").
		Preload("Source")
}

func (s *ResourceService) preloadSharedItems(db *gorm.DB) *gorm.DB {
	return db.
		Preload("Brand").
		Preload("Category").
		Preload("Style").
		Preload("Season").
		Preload("Source").
		Preload("CatalogEntry").
		Preload("Coordinate")
}

func (s *ResourceService) preloadPricePlans(db *gorm.DB) *gorm.DB {
	return db.Preload("SharedItem")
}

func (s *ResourceService) findCatalogEntry(db *gorm.DB, publicID string) (*model.CatalogEntry, error) {
	var item model.CatalogEntry
	if err := s.preloadCatalogEntries(repository.Active(db)).
		Where("public_id = ?", strings.TrimSpace(publicID)).
		First(&item).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, NotFound("catalog entry not found")
		}
		return nil, err
	}
	return &item, nil
}

func (s *ResourceService) findSharedItem(db *gorm.DB, publicID string) (*model.SharedItem, error) {
	var item model.SharedItem
	if err := s.preloadSharedItems(repository.Active(db)).
		Where("public_id = ?", strings.TrimSpace(publicID)).
		First(&item).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, NotFound("shared item not found")
		}
		return nil, err
	}
	return &item, nil
}

func (s *ResourceService) findPricePlan(db *gorm.DB, publicID string) (*model.PricePlan, error) {
	var item model.PricePlan
	if err := s.preloadPricePlans(repository.Active(db)).
		Where("public_id = ?", strings.TrimSpace(publicID)).
		First(&item).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, NotFound("price plan not found")
		}
		return nil, err
	}
	return &item, nil
}

func (s *ResourceService) createCatalogEntry(tx *gorm.DB, input CatalogEntryUpsertInput) (any, error) {
	item, err := s.buildCatalogEntry(tx, input)
	if err != nil {
		return nil, err
	}

	item.BaseResource = model.NewBaseResource()
	if err := tx.Create(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCatalogEntries, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}

	loaded, err := s.findCatalogEntry(tx, item.PublicID)
	if err != nil {
		return nil, err
	}
	return syncdto.AdminCatalogEntryFromModel(*loaded), nil
}

func (s *ResourceService) updateCatalogEntry(tx *gorm.DB, publicID string, input CatalogEntryUpsertInput) (any, error) {
	item, err := s.findCatalogEntry(tx, publicID)
	if err != nil {
		return nil, err
	}

	updated, err := s.buildCatalogEntry(tx, input)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		if err := ensureDependenciesPublished(updated.Brand, updated.Category, updated.Style, updated.Season, updated.Source); err != nil {
			return nil, err
		}
	}

	item.Name = updated.Name
	item.BrandID = updated.BrandID
	item.Brand = updated.Brand
	item.CategoryID = updated.CategoryID
	item.Category = updated.Category
	item.StyleID = updated.StyleID
	item.Style = updated.Style
	item.SeasonID = updated.SeasonID
	item.Season = updated.Season
	item.SourceID = updated.SourceID
	item.Source = updated.Source
	item.SeriesName = updated.SeriesName
	item.ReferenceURL = updated.ReferenceURL
	item.ImageURLs = updated.ImageURLs
	item.Colors = updated.Colors
	item.Size = updated.Size
	item.Description = updated.Description
	item.Touch()

	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCatalogEntries, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}

	loaded, err := s.findCatalogEntry(tx, item.PublicID)
	if err != nil {
		return nil, err
	}
	return syncdto.AdminCatalogEntryFromModel(*loaded), nil
}

func (s *ResourceService) publishCatalogEntry(tx *gorm.DB, publicID string) (any, error) {
	item, err := s.findCatalogEntry(tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminCatalogEntryFromModel(*item), nil
	}
	if err := ensureDependenciesPublished(item.Brand, item.Category, item.Style, item.Season, item.Source); err != nil {
		return nil, err
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCatalogEntries, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCatalogEntryFromModel(*item), nil
}

func (s *ResourceService) unpublishCatalogEntry(tx *gorm.DB, publicID string) (any, error) {
	item, err := s.findCatalogEntry(tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminCatalogEntryFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "catalog_entry_id", item.ID, "cannot unpublish catalog entry while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCatalogEntries, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCatalogEntryFromModel(*item), nil
}

func (s *ResourceService) deleteCatalogEntry(tx *gorm.DB, publicID string) error {
	item, err := s.findCatalogEntry(tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "catalog_entry_id", item.ID, "cannot delete catalog entry while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceCatalogEntries, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createCoordinate(tx *gorm.DB, input CoordinateUpsertInput) (any, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "coordinate name is required"); err != nil {
		return nil, err
	}

	item := model.Coordinate{
		BaseResource: model.NewBaseResource(),
		Name:         name,
		Description:  strings.TrimSpace(input.Description),
		ImageURLs:    normalizeSlice(input.ImageURLs),
	}
	if err := tx.Create(&item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCoordinates, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCoordinateFromModel(item), nil
}

func (s *ResourceService) updateCoordinate(tx *gorm.DB, publicID string, input CoordinateUpsertInput) (any, error) {
	item, err := findActiveByPublicID[model.Coordinate](tx, publicID)
	if err != nil {
		return nil, err
	}

	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "coordinate name is required"); err != nil {
		return nil, err
	}

	item.Name = name
	item.Description = strings.TrimSpace(input.Description)
	item.ImageURLs = normalizeSlice(input.ImageURLs)
	item.Touch()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCoordinates, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCoordinateFromModel(*item), nil
}

func (s *ResourceService) publishCoordinate(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Coordinate](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminCoordinateFromModel(*item), nil
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCoordinates, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCoordinateFromModel(*item), nil
}

func (s *ResourceService) unpublishCoordinate(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Coordinate](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminCoordinateFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "coordinate_id", item.ID, "cannot unpublish coordinate while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCoordinates, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCoordinateFromModel(*item), nil
}

func (s *ResourceService) deleteCoordinate(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.Coordinate](tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "coordinate_id", item.ID, "cannot delete coordinate while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceCoordinates, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createSharedItem(tx *gorm.DB, input SharedItemUpsertInput) (any, error) {
	item, err := s.buildSharedItem(tx, input)
	if err != nil {
		return nil, err
	}

	item.BaseResource = model.NewBaseResource()
	if err := tx.Create(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceItems, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}

	loaded, err := s.findSharedItem(tx, item.PublicID)
	if err != nil {
		return nil, err
	}
	return syncdto.AdminSharedItemFromModel(*loaded), nil
}

func (s *ResourceService) updateSharedItem(tx *gorm.DB, publicID string, input SharedItemUpsertInput) (any, error) {
	item, err := s.findSharedItem(tx, publicID)
	if err != nil {
		return nil, err
	}

	updated, err := s.buildSharedItem(tx, input)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		if err := ensureRequiredAssociationPublished(updated.Brand, "published shared items require a published brand"); err != nil {
			return nil, err
		}
		if err := ensureRequiredAssociationPublished(updated.Category, "published shared items require a published category"); err != nil {
			return nil, err
		}
		if err := ensureDependenciesPublished(updated.Style, updated.Season, updated.Source, updated.CatalogEntry, updated.Coordinate); err != nil {
			return nil, err
		}
	}

	item.Name = updated.Name
	item.Description = updated.Description
	item.BrandID = updated.BrandID
	item.Brand = updated.Brand
	item.CategoryID = updated.CategoryID
	item.Category = updated.Category
	item.StyleID = updated.StyleID
	item.Style = updated.Style
	item.SeasonID = updated.SeasonID
	item.Season = updated.Season
	item.SourceID = updated.SourceID
	item.Source = updated.Source
	item.CatalogEntryID = updated.CatalogEntryID
	item.CatalogEntry = updated.CatalogEntry
	item.CoordinateID = updated.CoordinateID
	item.Coordinate = updated.Coordinate
	item.CoordinateOrder = updated.CoordinateOrder
	item.ImageURLs = updated.ImageURLs
	item.Colors = updated.Colors
	item.Size = updated.Size
	item.SizeChartImageURL = updated.SizeChartImageURL
	item.Touch()

	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceItems, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}

	loaded, err := s.findSharedItem(tx, item.PublicID)
	if err != nil {
		return nil, err
	}
	return syncdto.AdminSharedItemFromModel(*loaded), nil
}

func (s *ResourceService) publishSharedItem(tx *gorm.DB, publicID string) (any, error) {
	item, err := s.findSharedItem(tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminSharedItemFromModel(*item), nil
	}
	if err := ensureRequiredAssociationPublished(item.Brand, "published shared items require a published brand"); err != nil {
		return nil, err
	}
	if err := ensureRequiredAssociationPublished(item.Category, "published shared items require a published category"); err != nil {
		return nil, err
	}
	if err := ensureDependenciesPublished(item.Style, item.Season, item.Source, item.CatalogEntry, item.Coordinate); err != nil {
		return nil, err
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceItems, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSharedItemFromModel(*item), nil
}

func (s *ResourceService) unpublishSharedItem(tx *gorm.DB, publicID string) (any, error) {
	item, err := s.findSharedItem(tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminSharedItemFromModel(*item), nil
	}
	if err := s.unpublishPricePlansBySharedItem(tx, item.ID); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceItems, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSharedItemFromModel(*item), nil
}

func (s *ResourceService) deleteSharedItem(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.SharedItem](tx, publicID)
	if err != nil {
		return err
	}
	if err := s.deletePricePlansBySharedItem(tx, item.ID); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceItems, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createPricePlan(tx *gorm.DB, input PricePlanUpsertInput) (any, error) {
	item, err := s.buildPricePlan(tx, input)
	if err != nil {
		return nil, err
	}

	item.BaseResource = model.NewBaseResource()
	if err := tx.Create(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourcePricePlans, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}

	loaded, err := s.findPricePlan(tx, item.PublicID)
	if err != nil {
		return nil, err
	}
	return syncdto.AdminPricePlanFromModel(*loaded), nil
}

func (s *ResourceService) updatePricePlan(tx *gorm.DB, publicID string, input PricePlanUpsertInput) (any, error) {
	item, err := s.findPricePlan(tx, publicID)
	if err != nil {
		return nil, err
	}

	updated, err := s.buildPricePlan(tx, input)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		if err := ensureRequiredAssociationPublished(updated.SharedItem, "published price plans require a published shared item"); err != nil {
			return nil, err
		}
	}

	item.SharedItemID = updated.SharedItemID
	item.SharedItem = updated.SharedItem
	item.PriceType = updated.PriceType
	item.TotalPrice = updated.TotalPrice
	item.Deposit = updated.Deposit
	item.Balance = updated.Balance
	item.DepositDueAt = updated.DepositDueAt
	item.BalanceDueAt = updated.BalanceDueAt
	item.Touch()

	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourcePricePlans, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}

	loaded, err := s.findPricePlan(tx, item.PublicID)
	if err != nil {
		return nil, err
	}
	return syncdto.AdminPricePlanFromModel(*loaded), nil
}

func (s *ResourceService) publishPricePlan(tx *gorm.DB, publicID string) (any, error) {
	item, err := s.findPricePlan(tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminPricePlanFromModel(*item), nil
	}
	if err := ensureRequiredAssociationPublished(item.SharedItem, "published price plans require a published shared item"); err != nil {
		return nil, err
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourcePricePlans, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminPricePlanFromModel(*item), nil
}

func (s *ResourceService) unpublishPricePlan(tx *gorm.DB, publicID string) (any, error) {
	item, err := s.findPricePlan(tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminPricePlanFromModel(*item), nil
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourcePricePlans, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminPricePlanFromModel(*item), nil
}

func (s *ResourceService) deletePricePlan(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.PricePlan](tx, publicID)
	if err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourcePricePlans, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) buildCatalogEntry(tx *gorm.DB, input CatalogEntryUpsertInput) (*model.CatalogEntry, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "catalog entry name is required"); err != nil {
		return nil, err
	}

	brandID, brand, err := resolveOptionalReference[model.Brand](tx, input.BrandPublicID, "brand not found")
	if err != nil {
		return nil, err
	}
	categoryID, category, err := resolveOptionalReference[model.Category](tx, input.CategoryPublicID, "category not found")
	if err != nil {
		return nil, err
	}
	styleID, style, err := resolveOptionalReference[model.Style](tx, input.StylePublicID, "style not found")
	if err != nil {
		return nil, err
	}
	seasonID, season, err := resolveOptionalReference[model.Season](tx, input.SeasonPublicID, "season not found")
	if err != nil {
		return nil, err
	}
	sourceID, source, err := resolveOptionalReference[model.Source](tx, input.SourcePublicID, "source not found")
	if err != nil {
		return nil, err
	}

	return &model.CatalogEntry{
		Name:         name,
		BrandID:      brandID,
		Brand:        brand,
		CategoryID:   categoryID,
		Category:     category,
		StyleID:      styleID,
		Style:        style,
		SeasonID:     seasonID,
		Season:       season,
		SourceID:     sourceID,
		Source:       source,
		SeriesName:   normalizeOptional(input.SeriesName),
		ReferenceURL: normalizeOptional(input.ReferenceURL),
		ImageURLs:    normalizeSlice(input.ImageURLs),
		Colors:       normalizeSlice(input.Colors),
		Size:         normalizeOptional(input.Size),
		Description:  strings.TrimSpace(input.Description),
	}, nil
}

func (s *ResourceService) buildSharedItem(tx *gorm.DB, input SharedItemUpsertInput) (*model.SharedItem, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "shared item name is required"); err != nil {
		return nil, err
	}

	brandPublicID := strings.TrimSpace(input.BrandPublicID)
	categoryPublicID := strings.TrimSpace(input.CategoryPublicID)
	brandID, brand, err := resolveRequiredReference[model.Brand](tx, &brandPublicID, "brand not found")
	if err != nil {
		return nil, err
	}
	categoryID, category, err := resolveRequiredReference[model.Category](tx, &categoryPublicID, "category not found")
	if err != nil {
		return nil, err
	}
	styleID, style, err := resolveOptionalReference[model.Style](tx, input.StylePublicID, "style not found")
	if err != nil {
		return nil, err
	}
	seasonID, season, err := resolveOptionalReference[model.Season](tx, input.SeasonPublicID, "season not found")
	if err != nil {
		return nil, err
	}
	sourceID, source, err := resolveOptionalReference[model.Source](tx, input.SourcePublicID, "source not found")
	if err != nil {
		return nil, err
	}
	catalogEntryID, catalogEntry, err := resolveOptionalReference[model.CatalogEntry](tx, input.CatalogEntryPublicID, "catalog entry not found")
	if err != nil {
		return nil, err
	}
	coordinateID, coordinate, err := resolveOptionalReference[model.Coordinate](tx, input.CoordinatePublicID, "coordinate not found")
	if err != nil {
		return nil, err
	}

	coordinateOrder := 0
	if coordinateID != nil {
		coordinateOrder = input.CoordinateOrder
	}

	return &model.SharedItem{
		Name:              name,
		Description:       strings.TrimSpace(input.Description),
		BrandID:           brandID,
		Brand:             brand,
		CategoryID:        categoryID,
		Category:          category,
		StyleID:           styleID,
		Style:             style,
		SeasonID:          seasonID,
		Season:            season,
		SourceID:          sourceID,
		Source:            source,
		CatalogEntryID:    catalogEntryID,
		CatalogEntry:      catalogEntry,
		CoordinateID:      coordinateID,
		Coordinate:        coordinate,
		CoordinateOrder:   coordinateOrder,
		ImageURLs:         normalizeSlice(input.ImageURLs),
		Colors:            normalizeSlice(input.Colors),
		Size:              normalizeOptional(input.Size),
		SizeChartImageURL: normalizeOptional(input.SizeChartImageURL),
	}, nil
}

func (s *ResourceService) buildPricePlan(tx *gorm.DB, input PricePlanUpsertInput) (*model.PricePlan, error) {
	if err := validatePricePlanInput(input); err != nil {
		return nil, err
	}

	sharedItemPublicID := strings.TrimSpace(input.SharedItemPublicID)
	sharedItemID, sharedItem, err := resolveRequiredReference[model.SharedItem](tx, &sharedItemPublicID, "shared item not found")
	if err != nil {
		return nil, err
	}

	return &model.PricePlan{
		SharedItemID: *sharedItemID,
		SharedItem:   sharedItem,
		PriceType:    strings.ToUpper(strings.TrimSpace(input.PriceType)),
		TotalPrice:   input.TotalPrice,
		Deposit:      copyFloatPointer(input.Deposit),
		Balance:      copyFloatPointer(input.Balance),
		DepositDueAt: copyInt64Pointer(input.DepositDueAt),
		BalanceDueAt: copyInt64Pointer(input.BalanceDueAt),
	}, nil
}

func (s *ResourceService) unpublishPricePlansBySharedItem(tx *gorm.DB, sharedItemID uint) error {
	var pricePlans []model.PricePlan
	if err := repository.Active(tx).Where("shared_item_id = ?", sharedItemID).Find(&pricePlans).Error; err != nil {
		return err
	}

	for i := range pricePlans {
		if pricePlans[i].PublishStatus != model.PublishStatusPublished {
			continue
		}
		pricePlans[i].Unpublish()
		if err := tx.Save(&pricePlans[i]).Error; err != nil {
			return err
		}
		if err := logChange(tx, model.ResourcePricePlans, model.ChangeOperationDelete, pricePlans[i].PublicID); err != nil {
			return err
		}
	}
	return nil
}

func (s *ResourceService) deletePricePlansBySharedItem(tx *gorm.DB, sharedItemID uint) error {
	var pricePlans []model.PricePlan
	if err := repository.Active(tx).Where("shared_item_id = ?", sharedItemID).Find(&pricePlans).Error; err != nil {
		return err
	}

	for i := range pricePlans {
		pricePlans[i].SoftDelete()
		if err := tx.Save(&pricePlans[i]).Error; err != nil {
			return err
		}
		if err := logChange(tx, model.ResourcePricePlans, model.ChangeOperationDelete, pricePlans[i].PublicID); err != nil {
			return err
		}
	}
	return nil
}
