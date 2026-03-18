package model

import (
	"database/sql/driver"
	"encoding/json"
	"errors"
	"time"

	"github.com/google/uuid"
)

type PublishStatus string

const (
	PublishStatusDraft     PublishStatus = "draft"
	PublishStatusPublished PublishStatus = "published"
)

type ChangeOperation string

const (
	ChangeOperationUpsert ChangeOperation = "upsert"
	ChangeOperationDelete ChangeOperation = "delete"
)

type ResourceType string

const (
	ResourceBrands         ResourceType = "brands"
	ResourceCategories     ResourceType = "categories"
	ResourceStyles         ResourceType = "styles"
	ResourceSeasons        ResourceType = "seasons"
	ResourceSources        ResourceType = "sources"
	ResourceCatalogEntries ResourceType = "catalog_entries"
	ResourceItems          ResourceType = "shared_items"
	ResourceCoordinates    ResourceType = "coordinates"
	ResourcePricePlans     ResourceType = "price_plans"
)

func AllResourceTypes() []ResourceType {
	return []ResourceType{
		ResourceBrands,
		ResourceCategories,
		ResourceStyles,
		ResourceSeasons,
		ResourceSources,
		ResourceCatalogEntries,
		ResourceItems,
		ResourceCoordinates,
		ResourcePricePlans,
	}
}

type StringSlice []string

func (s StringSlice) Value() (driver.Value, error) {
	if s == nil {
		return "[]", nil
	}
	encoded, err := json.Marshal([]string(s))
	if err != nil {
		return nil, err
	}
	return string(encoded), nil
}

func (s *StringSlice) Scan(value any) error {
	if value == nil {
		*s = StringSlice{}
		return nil
	}

	var raw []byte
	switch typed := value.(type) {
	case []byte:
		raw = typed
	case string:
		raw = []byte(typed)
	default:
		return errors.New("unsupported StringSlice scan type")
	}

	if len(raw) == 0 {
		*s = StringSlice{}
		return nil
	}

	var decoded []string
	if err := json.Unmarshal(raw, &decoded); err != nil {
		return err
	}
	*s = decoded
	return nil
}

type BaseResource struct {
	ID            uint          `gorm:"primaryKey"`
	PublicID      string        `gorm:"column:public_id;size:36;not null;uniqueIndex"`
	PublishStatus PublishStatus `gorm:"column:publish_status;size:16;not null;index"`
	CreatedAt     int64         `gorm:"column:created_at;not null;index"`
	UpdatedAt     int64         `gorm:"column:updated_at;not null;index"`
	DeletedAt     *int64        `gorm:"column:deleted_at;index"`
}

func NewBaseResource() BaseResource {
	now := NowMilli()
	return BaseResource{
		PublicID:      uuid.NewString(),
		PublishStatus: PublishStatusDraft,
		CreatedAt:     now,
		UpdatedAt:     now,
	}
}

func (b *BaseResource) Touch() {
	b.UpdatedAt = NowMilli()
}

func (b *BaseResource) Publish() {
	b.PublishStatus = PublishStatusPublished
	b.Touch()
}

func (b *BaseResource) Unpublish() {
	b.PublishStatus = PublishStatusDraft
	b.Touch()
}

func (b *BaseResource) SoftDelete() {
	now := NowMilli()
	b.DeletedAt = &now
	b.UpdatedAt = now
}

func (b *BaseResource) IsDeleted() bool {
	return b.DeletedAt != nil
}

func (b *BaseResource) GetID() uint {
	return b.ID
}

func (b *BaseResource) GetPublicID() string {
	return b.PublicID
}

func (b *BaseResource) GetStatus() PublishStatus {
	return b.PublishStatus
}

func NowMilli() int64 {
	return time.Now().UnixMilli()
}
