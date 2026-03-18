package repository

import (
	"github.com/glebarez/sqlite"
	"github.com/lolita/app/backend/internal/config"
	"github.com/lolita/app/backend/internal/model"
	"gorm.io/gorm"
)

type Store struct {
	db *gorm.DB
}

func New(cfg config.Config) (*Store, error) {
	db, err := gorm.Open(sqlite.Open(cfg.Database.Path), &gorm.Config{})
	if err != nil {
		return nil, err
	}
	if err := db.Exec("PRAGMA foreign_keys = ON").Error; err != nil {
		return nil, err
	}
	return &Store{db: db}, nil
}

func (s *Store) AutoMigrate() error {
	return s.db.AutoMigrate(
		&model.Brand{},
		&model.Category{},
		&model.Style{},
		&model.Season{},
		&model.Source{},
		&model.CatalogEntry{},
		&model.Coordinate{},
		&model.SharedItem{},
		&model.PricePlan{},
		&model.ChangeEvent{},
	)
}

func (s *Store) DB() *gorm.DB {
	return s.db
}

func (s *Store) Close() error {
	sqlDB, err := s.db.DB()
	if err != nil {
		return err
	}
	return sqlDB.Close()
}

func (s *Store) WithTx(fn func(tx *gorm.DB) error) error {
	return s.db.Transaction(fn)
}

func Active(db *gorm.DB) *gorm.DB {
	return db.Where("deleted_at IS NULL")
}

func Published(db *gorm.DB) *gorm.DB {
	return Active(db).Where("publish_status = ?", model.PublishStatusPublished)
}
