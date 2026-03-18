package sync

import "github.com/lolita/app/backend/internal/model"

const SchemaVersion = 1

type SnapshotResponse struct {
	SchemaVersion int          `json:"schemaVersion"`
	AssetBaseURL  string       `json:"assetBaseUrl"`
	NextCursor    uint64       `json:"nextCursor"`
	Data          SnapshotData `json:"data"`
}

type SnapshotData struct {
	Brands         []BrandDTO        `json:"brands"`
	Categories     []CategoryDTO     `json:"categories"`
	Styles         []StyleDTO        `json:"styles"`
	Seasons        []SeasonDTO       `json:"seasons"`
	Sources        []SourceDTO       `json:"sources"`
	CatalogEntries []CatalogEntryDTO `json:"catalogEntries"`
	Items          []SharedItemDTO   `json:"items"`
	Coordinates    []CoordinateDTO   `json:"coordinates"`
	PricePlans     []PricePlanDTO    `json:"pricePlans"`
}

type ChangesResponse struct {
	SchemaVersion int         `json:"schemaVersion"`
	AssetBaseURL  string      `json:"assetBaseUrl"`
	NextCursor    uint64      `json:"nextCursor"`
	Changes       ChangeBatch `json:"changes"`
}

type ChangeBatch struct {
	Brands         ResourceChangeSet[BrandDTO]        `json:"brands"`
	Categories     ResourceChangeSet[CategoryDTO]     `json:"categories"`
	Styles         ResourceChangeSet[StyleDTO]        `json:"styles"`
	Seasons        ResourceChangeSet[SeasonDTO]       `json:"seasons"`
	Sources        ResourceChangeSet[SourceDTO]       `json:"sources"`
	CatalogEntries ResourceChangeSet[CatalogEntryDTO] `json:"catalogEntries"`
	Items          ResourceChangeSet[SharedItemDTO]   `json:"items"`
	Coordinates    ResourceChangeSet[CoordinateDTO]   `json:"coordinates"`
	PricePlans     ResourceChangeSet[PricePlanDTO]    `json:"pricePlans"`
}

type ResourceChangeSet[T any] struct {
	Upserts          []T      `json:"upserts"`
	DeletedPublicIDs []string `json:"deletedPublicIds"`
}

type BrandDTO struct {
	PublicID  string `json:"publicId"`
	Name      string `json:"name"`
	LogoURL   string `json:"logoUrl,omitempty"`
	UpdatedAt int64  `json:"updatedAt"`
}

type CategoryDTO struct {
	PublicID  string `json:"publicId"`
	Name      string `json:"name"`
	Group     string `json:"group"`
	UpdatedAt int64  `json:"updatedAt"`
}

type StyleDTO struct {
	PublicID  string `json:"publicId"`
	Name      string `json:"name"`
	UpdatedAt int64  `json:"updatedAt"`
}

type SeasonDTO struct {
	PublicID  string `json:"publicId"`
	Name      string `json:"name"`
	UpdatedAt int64  `json:"updatedAt"`
}

type SourceDTO struct {
	PublicID  string `json:"publicId"`
	Name      string `json:"name"`
	UpdatedAt int64  `json:"updatedAt"`
}

type CatalogEntryDTO struct {
	PublicID         string   `json:"publicId"`
	Name             string   `json:"name"`
	BrandPublicID    string   `json:"brandPublicId,omitempty"`
	CategoryPublicID string   `json:"categoryPublicId,omitempty"`
	StylePublicID    string   `json:"stylePublicId,omitempty"`
	SeasonPublicID   string   `json:"seasonPublicId,omitempty"`
	SourcePublicID   string   `json:"sourcePublicId,omitempty"`
	SeriesName       string   `json:"seriesName,omitempty"`
	ReferenceURL     string   `json:"referenceUrl,omitempty"`
	ImageURLs        []string `json:"imageUrls"`
	Colors           []string `json:"colors"`
	Size             string   `json:"size,omitempty"`
	Description      string   `json:"description"`
	UpdatedAt        int64    `json:"updatedAt"`
}

type SharedItemDTO struct {
	PublicID             string   `json:"publicId"`
	Name                 string   `json:"name"`
	Description          string   `json:"description"`
	BrandPublicID        string   `json:"brandPublicId,omitempty"`
	CategoryPublicID     string   `json:"categoryPublicId,omitempty"`
	StylePublicID        string   `json:"stylePublicId,omitempty"`
	SeasonPublicID       string   `json:"seasonPublicId,omitempty"`
	SourcePublicID       string   `json:"sourcePublicId,omitempty"`
	CatalogEntryPublicID string   `json:"catalogEntryPublicId,omitempty"`
	CoordinatePublicID   string   `json:"coordinatePublicId,omitempty"`
	CoordinateOrder      int      `json:"coordinateOrder"`
	ImageURLs            []string `json:"imageUrls"`
	Colors               []string `json:"colors"`
	Size                 string   `json:"size,omitempty"`
	SizeChartImageURL    string   `json:"sizeChartImageUrl,omitempty"`
	UpdatedAt            int64    `json:"updatedAt"`
}

type CoordinateDTO struct {
	PublicID    string   `json:"publicId"`
	Name        string   `json:"name"`
	Description string   `json:"description"`
	ImageURLs   []string `json:"imageUrls"`
	UpdatedAt   int64    `json:"updatedAt"`
}

type PricePlanDTO struct {
	PublicID           string   `json:"publicId"`
	SharedItemPublicID string   `json:"sharedItemPublicId"`
	PriceType          string   `json:"priceType"`
	TotalPrice         float64  `json:"totalPrice"`
	Deposit            *float64 `json:"deposit,omitempty"`
	Balance            *float64 `json:"balance,omitempty"`
	DepositDueAt       *int64   `json:"depositDueAt,omitempty"`
	BalanceDueAt       *int64   `json:"balanceDueAt,omitempty"`
	UpdatedAt          int64    `json:"updatedAt"`
}

type AdminBrandDTO struct {
	BrandDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminCategoryDTO struct {
	CategoryDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminStyleDTO struct {
	StyleDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminSeasonDTO struct {
	SeasonDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminSourceDTO struct {
	SourceDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminCatalogEntryDTO struct {
	CatalogEntryDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminSharedItemDTO struct {
	SharedItemDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminCoordinateDTO struct {
	CoordinateDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

type AdminPricePlanDTO struct {
	PricePlanDTO
	PublishStatus string `json:"publishStatus"`
	CreatedAt     int64  `json:"createdAt"`
}

func BrandFromModel(item model.Brand) BrandDTO {
	return BrandDTO{
		PublicID:  item.PublicID,
		Name:      item.Name,
		LogoURL:   item.LogoURL,
		UpdatedAt: item.UpdatedAt,
	}
}

func AdminBrandFromModel(item model.Brand) AdminBrandDTO {
	return AdminBrandDTO{
		BrandDTO:      BrandFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func CategoryFromModel(item model.Category) CategoryDTO {
	return CategoryDTO{
		PublicID:  item.PublicID,
		Name:      item.Name,
		Group:     item.Group,
		UpdatedAt: item.UpdatedAt,
	}
}

func AdminCategoryFromModel(item model.Category) AdminCategoryDTO {
	return AdminCategoryDTO{
		CategoryDTO:   CategoryFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func StyleFromModel(item model.Style) StyleDTO {
	return StyleDTO{
		PublicID:  item.PublicID,
		Name:      item.Name,
		UpdatedAt: item.UpdatedAt,
	}
}

func AdminStyleFromModel(item model.Style) AdminStyleDTO {
	return AdminStyleDTO{
		StyleDTO:      StyleFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func SeasonFromModel(item model.Season) SeasonDTO {
	return SeasonDTO{
		PublicID:  item.PublicID,
		Name:      item.Name,
		UpdatedAt: item.UpdatedAt,
	}
}

func AdminSeasonFromModel(item model.Season) AdminSeasonDTO {
	return AdminSeasonDTO{
		SeasonDTO:     SeasonFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func SourceFromModel(item model.Source) SourceDTO {
	return SourceDTO{
		PublicID:  item.PublicID,
		Name:      item.Name,
		UpdatedAt: item.UpdatedAt,
	}
}

func AdminSourceFromModel(item model.Source) AdminSourceDTO {
	return AdminSourceDTO{
		SourceDTO:     SourceFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func CatalogEntryFromModel(item model.CatalogEntry) CatalogEntryDTO {
	return CatalogEntryDTO{
		PublicID:         item.PublicID,
		Name:             item.Name,
		BrandPublicID:    nestedPublicID(item.Brand),
		CategoryPublicID: nestedPublicID(item.Category),
		StylePublicID:    nestedPublicID(item.Style),
		SeasonPublicID:   nestedPublicID(item.Season),
		SourcePublicID:   nestedPublicID(item.Source),
		SeriesName:       item.SeriesName,
		ReferenceURL:     item.ReferenceURL,
		ImageURLs:        []string(item.ImageURLs),
		Colors:           []string(item.Colors),
		Size:             item.Size,
		Description:      item.Description,
		UpdatedAt:        item.UpdatedAt,
	}
}

func AdminCatalogEntryFromModel(item model.CatalogEntry) AdminCatalogEntryDTO {
	return AdminCatalogEntryDTO{
		CatalogEntryDTO: CatalogEntryFromModel(item),
		PublishStatus:   string(item.PublishStatus),
		CreatedAt:       item.CreatedAt,
	}
}

func SharedItemFromModel(item model.SharedItem) SharedItemDTO {
	return SharedItemDTO{
		PublicID:             item.PublicID,
		Name:                 item.Name,
		Description:          item.Description,
		BrandPublicID:        nestedPublicID(item.Brand),
		CategoryPublicID:     nestedPublicID(item.Category),
		StylePublicID:        nestedPublicID(item.Style),
		SeasonPublicID:       nestedPublicID(item.Season),
		SourcePublicID:       nestedPublicID(item.Source),
		CatalogEntryPublicID: nestedPublicID(item.CatalogEntry),
		CoordinatePublicID:   nestedPublicID(item.Coordinate),
		CoordinateOrder:      item.CoordinateOrder,
		ImageURLs:            []string(item.ImageURLs),
		Colors:               []string(item.Colors),
		Size:                 item.Size,
		SizeChartImageURL:    item.SizeChartImageURL,
		UpdatedAt:            item.UpdatedAt,
	}
}

func AdminSharedItemFromModel(item model.SharedItem) AdminSharedItemDTO {
	return AdminSharedItemDTO{
		SharedItemDTO: SharedItemFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func CoordinateFromModel(item model.Coordinate) CoordinateDTO {
	return CoordinateDTO{
		PublicID:    item.PublicID,
		Name:        item.Name,
		Description: item.Description,
		ImageURLs:   []string(item.ImageURLs),
		UpdatedAt:   item.UpdatedAt,
	}
}

func AdminCoordinateFromModel(item model.Coordinate) AdminCoordinateDTO {
	return AdminCoordinateDTO{
		CoordinateDTO: CoordinateFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func PricePlanFromModel(item model.PricePlan) PricePlanDTO {
	return PricePlanDTO{
		PublicID:           item.PublicID,
		SharedItemPublicID: nestedPublicID(item.SharedItem),
		PriceType:          item.PriceType,
		TotalPrice:         item.TotalPrice,
		Deposit:            item.Deposit,
		Balance:            item.Balance,
		DepositDueAt:       item.DepositDueAt,
		BalanceDueAt:       item.BalanceDueAt,
		UpdatedAt:          item.UpdatedAt,
	}
}

func AdminPricePlanFromModel(item model.PricePlan) AdminPricePlanDTO {
	return AdminPricePlanDTO{
		PricePlanDTO:  PricePlanFromModel(item),
		PublishStatus: string(item.PublishStatus),
		CreatedAt:     item.CreatedAt,
	}
}

func nestedPublicID(item any) string {
	switch typed := item.(type) {
	case *model.Brand:
		if typed != nil {
			return typed.PublicID
		}
	case *model.Category:
		if typed != nil {
			return typed.PublicID
		}
	case *model.Style:
		if typed != nil {
			return typed.PublicID
		}
	case *model.Season:
		if typed != nil {
			return typed.PublicID
		}
	case *model.Source:
		if typed != nil {
			return typed.PublicID
		}
	case *model.CatalogEntry:
		if typed != nil {
			return typed.PublicID
		}
	case *model.SharedItem:
		if typed != nil {
			return typed.PublicID
		}
	case *model.Coordinate:
		if typed != nil {
			return typed.PublicID
		}
	case *model.PricePlan:
		if typed != nil {
			return typed.PublicID
		}
	}
	return ""
}
