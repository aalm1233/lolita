package service

import (
	"strings"

	"github.com/lolita/app/backend/internal/model"
	syncdto "github.com/lolita/app/backend/internal/sync"
	"gorm.io/gorm"
)

func (s *ResourceService) createBrand(tx *gorm.DB, input BrandUpsertInput) (any, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "brand name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Brand](tx, name, 0); err != nil {
		return nil, err
	}

	item := model.Brand{
		BaseResource: model.NewBaseResource(),
		Name:         name,
		LogoURL:      normalizeOptional(input.LogoURL),
	}
	if err := tx.Create(&item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceBrands, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminBrandFromModel(item), nil
}

func (s *ResourceService) updateBrand(tx *gorm.DB, publicID string, input BrandUpsertInput) (any, error) {
	item, err := findActiveByPublicID[model.Brand](tx, publicID)
	if err != nil {
		return nil, err
	}

	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "brand name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Brand](tx, name, item.ID); err != nil {
		return nil, err
	}

	item.Name = name
	item.LogoURL = normalizeOptional(input.LogoURL)
	item.Touch()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceBrands, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminBrandFromModel(*item), nil
}

func (s *ResourceService) publishBrand(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Brand](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminBrandFromModel(*item), nil
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceBrands, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminBrandFromModel(*item), nil
}

func (s *ResourceService) unpublishBrand(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Brand](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminBrandFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.CatalogEntry](tx, "brand_id", item.ID, "cannot unpublish brand while published catalog entries reference it"); err != nil {
		return nil, err
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "brand_id", item.ID, "cannot unpublish brand while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceBrands, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminBrandFromModel(*item), nil
}

func (s *ResourceService) deleteBrand(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.Brand](tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.CatalogEntry](tx, "brand_id", item.ID, "cannot delete brand while catalog entries reference it"); err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "brand_id", item.ID, "cannot delete brand while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceBrands, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createCategory(tx *gorm.DB, input CategoryUpsertInput) (any, error) {
	name := strings.TrimSpace(input.Name)
	group := strings.ToUpper(strings.TrimSpace(input.Group))
	if err := validateName(name, "category name is required"); err != nil {
		return nil, err
	}
	if err := validateCategoryGroup(group); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Category](tx, name, 0); err != nil {
		return nil, err
	}

	item := model.Category{
		BaseResource: model.NewBaseResource(),
		Name:         name,
		Group:        group,
	}
	if err := tx.Create(&item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCategories, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCategoryFromModel(item), nil
}

func (s *ResourceService) updateCategory(tx *gorm.DB, publicID string, input CategoryUpsertInput) (any, error) {
	item, err := findActiveByPublicID[model.Category](tx, publicID)
	if err != nil {
		return nil, err
	}

	name := strings.TrimSpace(input.Name)
	group := strings.ToUpper(strings.TrimSpace(input.Group))
	if err := validateName(name, "category name is required"); err != nil {
		return nil, err
	}
	if err := validateCategoryGroup(group); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Category](tx, name, item.ID); err != nil {
		return nil, err
	}

	item.Name = name
	item.Group = group
	item.Touch()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCategories, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCategoryFromModel(*item), nil
}

func (s *ResourceService) publishCategory(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Category](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminCategoryFromModel(*item), nil
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCategories, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCategoryFromModel(*item), nil
}

func (s *ResourceService) unpublishCategory(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Category](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminCategoryFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.CatalogEntry](tx, "category_id", item.ID, "cannot unpublish category while published catalog entries reference it"); err != nil {
		return nil, err
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "category_id", item.ID, "cannot unpublish category while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceCategories, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminCategoryFromModel(*item), nil
}

func (s *ResourceService) deleteCategory(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.Category](tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.CatalogEntry](tx, "category_id", item.ID, "cannot delete category while catalog entries reference it"); err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "category_id", item.ID, "cannot delete category while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceCategories, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createStyle(tx *gorm.DB, input StyleUpsertInput) (any, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "style name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Style](tx, name, 0); err != nil {
		return nil, err
	}

	item := model.Style{
		BaseResource: model.NewBaseResource(),
		Name:         name,
	}
	if err := tx.Create(&item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceStyles, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminStyleFromModel(item), nil
}

func (s *ResourceService) updateStyle(tx *gorm.DB, publicID string, input StyleUpsertInput) (any, error) {
	item, err := findActiveByPublicID[model.Style](tx, publicID)
	if err != nil {
		return nil, err
	}

	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "style name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Style](tx, name, item.ID); err != nil {
		return nil, err
	}

	item.Name = name
	item.Touch()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceStyles, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminStyleFromModel(*item), nil
}

func (s *ResourceService) publishStyle(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Style](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminStyleFromModel(*item), nil
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceStyles, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminStyleFromModel(*item), nil
}

func (s *ResourceService) unpublishStyle(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Style](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminStyleFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.CatalogEntry](tx, "style_id", item.ID, "cannot unpublish style while published catalog entries reference it"); err != nil {
		return nil, err
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "style_id", item.ID, "cannot unpublish style while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceStyles, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminStyleFromModel(*item), nil
}

func (s *ResourceService) deleteStyle(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.Style](tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.CatalogEntry](tx, "style_id", item.ID, "cannot delete style while catalog entries reference it"); err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "style_id", item.ID, "cannot delete style while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceStyles, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createSeason(tx *gorm.DB, input SeasonUpsertInput) (any, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "season name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Season](tx, name, 0); err != nil {
		return nil, err
	}

	item := model.Season{
		BaseResource: model.NewBaseResource(),
		Name:         name,
	}
	if err := tx.Create(&item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSeasons, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSeasonFromModel(item), nil
}

func (s *ResourceService) updateSeason(tx *gorm.DB, publicID string, input SeasonUpsertInput) (any, error) {
	item, err := findActiveByPublicID[model.Season](tx, publicID)
	if err != nil {
		return nil, err
	}

	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "season name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Season](tx, name, item.ID); err != nil {
		return nil, err
	}

	item.Name = name
	item.Touch()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSeasons, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSeasonFromModel(*item), nil
}

func (s *ResourceService) publishSeason(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Season](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminSeasonFromModel(*item), nil
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSeasons, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSeasonFromModel(*item), nil
}

func (s *ResourceService) unpublishSeason(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Season](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminSeasonFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.CatalogEntry](tx, "season_id", item.ID, "cannot unpublish season while published catalog entries reference it"); err != nil {
		return nil, err
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "season_id", item.ID, "cannot unpublish season while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSeasons, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSeasonFromModel(*item), nil
}

func (s *ResourceService) deleteSeason(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.Season](tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.CatalogEntry](tx, "season_id", item.ID, "cannot delete season while catalog entries reference it"); err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "season_id", item.ID, "cannot delete season while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceSeasons, model.ChangeOperationDelete, item.PublicID)
}

func (s *ResourceService) createSource(tx *gorm.DB, input SourceUpsertInput) (any, error) {
	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "source name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Source](tx, name, 0); err != nil {
		return nil, err
	}

	item := model.Source{
		BaseResource: model.NewBaseResource(),
		Name:         name,
	}
	if err := tx.Create(&item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSources, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSourceFromModel(item), nil
}

func (s *ResourceService) updateSource(tx *gorm.DB, publicID string, input SourceUpsertInput) (any, error) {
	item, err := findActiveByPublicID[model.Source](tx, publicID)
	if err != nil {
		return nil, err
	}

	name := strings.TrimSpace(input.Name)
	if err := validateName(name, "source name is required"); err != nil {
		return nil, err
	}
	if err := ensureUniqueName[model.Source](tx, name, item.ID); err != nil {
		return nil, err
	}

	item.Name = name
	item.Touch()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSources, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSourceFromModel(*item), nil
}

func (s *ResourceService) publishSource(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Source](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusPublished {
		return syncdto.AdminSourceFromModel(*item), nil
	}

	item.Publish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSources, model.ChangeOperationUpsert, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSourceFromModel(*item), nil
}

func (s *ResourceService) unpublishSource(tx *gorm.DB, publicID string) (any, error) {
	item, err := findActiveByPublicID[model.Source](tx, publicID)
	if err != nil {
		return nil, err
	}
	if item.PublishStatus == model.PublishStatusDraft {
		return syncdto.AdminSourceFromModel(*item), nil
	}
	if err := ensurePublishedReferenceCount[model.CatalogEntry](tx, "source_id", item.ID, "cannot unpublish source while published catalog entries reference it"); err != nil {
		return nil, err
	}
	if err := ensurePublishedReferenceCount[model.SharedItem](tx, "source_id", item.ID, "cannot unpublish source while published shared items reference it"); err != nil {
		return nil, err
	}

	item.Unpublish()
	if err := tx.Save(item).Error; err != nil {
		return nil, err
	}
	if err := logChange(tx, model.ResourceSources, model.ChangeOperationDelete, item.PublicID); err != nil {
		return nil, err
	}
	return syncdto.AdminSourceFromModel(*item), nil
}

func (s *ResourceService) deleteSource(tx *gorm.DB, publicID string) error {
	item, err := findActiveByPublicID[model.Source](tx, publicID)
	if err != nil {
		return err
	}
	if err := ensureReferenceCount[model.CatalogEntry](tx, "source_id", item.ID, "cannot delete source while catalog entries reference it"); err != nil {
		return err
	}
	if err := ensureReferenceCount[model.SharedItem](tx, "source_id", item.ID, "cannot delete source while shared items reference it"); err != nil {
		return err
	}

	item.SoftDelete()
	if err := tx.Save(item).Error; err != nil {
		return err
	}
	return logChange(tx, model.ResourceSources, model.ChangeOperationDelete, item.PublicID)
}
