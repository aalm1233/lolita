package model

type Brand struct {
	BaseResource
	Name    string `gorm:"column:name;size:255;not null;index"`
	LogoURL string `gorm:"column:logo_url;size:1024"`
}

func (Brand) TableName() string { return "brands" }

type Category struct {
	BaseResource
	Name  string `gorm:"column:name;size:255;not null;index"`
	Group string `gorm:"column:category_group;size:32;not null"`
}

func (Category) TableName() string { return "categories" }

type Style struct {
	BaseResource
	Name string `gorm:"column:name;size:255;not null;index"`
}

func (Style) TableName() string { return "styles" }

type Season struct {
	BaseResource
	Name string `gorm:"column:name;size:255;not null;index"`
}

func (Season) TableName() string { return "seasons" }

type Source struct {
	BaseResource
	Name string `gorm:"column:name;size:255;not null;index"`
}

func (Source) TableName() string { return "sources" }

type CatalogEntry struct {
	BaseResource
	Name         string      `gorm:"column:name;size:255;not null;index"`
	BrandID      *uint       `gorm:"column:brand_id;index"`
	Brand        *Brand      `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	CategoryID   *uint       `gorm:"column:category_id;index"`
	Category     *Category   `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	StyleID      *uint       `gorm:"column:style_id;index"`
	Style        *Style      `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	SeasonID     *uint       `gorm:"column:season_id;index"`
	Season       *Season     `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	SourceID     *uint       `gorm:"column:source_id;index"`
	Source       *Source     `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	SeriesName   string      `gorm:"column:series_name;size:255"`
	ReferenceURL string      `gorm:"column:reference_url;size:2048"`
	ImageURLs    StringSlice `gorm:"column:image_urls;type:text;not null"`
	Colors       StringSlice `gorm:"column:colors;type:text;not null"`
	Size         string      `gorm:"column:size;size:128"`
	Description  string      `gorm:"column:description;type:text;not null"`
}

func (CatalogEntry) TableName() string { return "catalog_entries" }

type Coordinate struct {
	BaseResource
	Name        string      `gorm:"column:name;size:255;not null;index"`
	Description string      `gorm:"column:description;type:text;not null"`
	ImageURLs   StringSlice `gorm:"column:image_urls;type:text;not null"`
}

func (Coordinate) TableName() string { return "coordinates" }

type SharedItem struct {
	BaseResource
	Name              string        `gorm:"column:name;size:255;not null;index"`
	Description       string        `gorm:"column:description;type:text;not null"`
	BrandID           *uint         `gorm:"column:brand_id;index"`
	Brand             *Brand        `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	CategoryID        *uint         `gorm:"column:category_id;index"`
	Category          *Category     `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	StyleID           *uint         `gorm:"column:style_id;index"`
	Style             *Style        `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	SeasonID          *uint         `gorm:"column:season_id;index"`
	Season            *Season       `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	SourceID          *uint         `gorm:"column:source_id;index"`
	Source            *Source       `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	CatalogEntryID    *uint         `gorm:"column:catalog_entry_id;index"`
	CatalogEntry      *CatalogEntry `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	CoordinateID      *uint         `gorm:"column:coordinate_id;index"`
	Coordinate        *Coordinate   `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	CoordinateOrder   int           `gorm:"column:coordinate_order;not null;default:0"`
	ImageURLs         StringSlice   `gorm:"column:image_urls;type:text;not null"`
	Colors            StringSlice   `gorm:"column:colors;type:text;not null"`
	Size              string        `gorm:"column:size;size:128"`
	SizeChartImageURL string        `gorm:"column:size_chart_image_url;size:1024"`
}

func (SharedItem) TableName() string { return "shared_items" }

type PricePlan struct {
	BaseResource
	SharedItemID uint        `gorm:"column:shared_item_id;not null;index"`
	SharedItem   *SharedItem `gorm:"constraint:OnUpdate:CASCADE,OnDelete:RESTRICT;"`
	PriceType    string      `gorm:"column:price_type;size:32;not null"`
	TotalPrice   float64     `gorm:"column:total_price;not null"`
	Deposit      *float64    `gorm:"column:deposit"`
	Balance      *float64    `gorm:"column:balance"`
	DepositDueAt *int64      `gorm:"column:deposit_due_at"`
	BalanceDueAt *int64      `gorm:"column:balance_due_at"`
}

func (PricePlan) TableName() string { return "price_plans" }

type ChangeEvent struct {
	Cursor       uint64          `gorm:"primaryKey;autoIncrement;column:cursor"`
	ResourceType ResourceType    `gorm:"column:resource_type;size:64;not null;index"`
	Operation    ChangeOperation `gorm:"column:operation;size:16;not null"`
	PublicID     string          `gorm:"column:public_id;size:36;not null;index"`
	ChangedAt    int64           `gorm:"column:changed_at;not null;index"`
}

func (ChangeEvent) TableName() string { return "change_events" }
