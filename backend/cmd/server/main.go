package main

import (
	"flag"
	"log"

	"github.com/lolita/app/backend/internal/api"
	"github.com/lolita/app/backend/internal/config"
	"github.com/lolita/app/backend/internal/repository"
)

func main() {
	configPath := flag.String("config", "./config.yaml", "path to backend config file")
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}
	if err := cfg.EnsureRuntimePaths(); err != nil {
		log.Fatalf("prepare runtime paths: %v", err)
	}

	store, err := repository.New(cfg)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	if err := store.AutoMigrate(); err != nil {
		log.Fatalf("migrate database: %v", err)
	}

	router := api.NewRouter(cfg, store)
	log.Printf("backend listening on %s", cfg.Server.Addr)
	if err := router.Run(cfg.Server.Addr); err != nil {
		log.Fatalf("run server: %v", err)
	}
}
