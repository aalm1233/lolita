package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/lolita/app/backend/internal/config"
	"github.com/lolita/app/backend/internal/middleware"
	"github.com/lolita/app/backend/internal/repository"
	"github.com/lolita/app/backend/internal/service"
)

type Server struct {
	config          config.Config
	authService     *service.AuthService
	uploadService   *service.UploadService
	resourceService *service.ResourceService
	syncService     *service.SyncService
}

func NewRouter(cfg config.Config, store *repository.Store) *gin.Engine {
	mode := cfg.Server.Mode
	switch mode {
	case gin.DebugMode, gin.ReleaseMode, gin.TestMode:
	default:
		mode = gin.DebugMode
	}
	gin.SetMode(mode)

	server := &Server{
		config:          cfg,
		authService:     service.NewAuthService(cfg.Auth),
		uploadService:   service.NewUploadService(cfg.Upload),
		resourceService: service.NewResourceService(store),
		syncService:     service.NewSyncService(store),
	}

	router := gin.New()
	router.Use(gin.Recovery())

	router.GET("/healthz", func(c *gin.Context) {
		respondSuccess(c, http.StatusOK, gin.H{"status": "ok"})
	})
	router.StaticFS("/uploads/images", http.Dir(cfg.Upload.Path))

	admin := router.Group("/api/admin")
	admin.POST("/auth/login", server.login)

	adminProtected := admin.Group("")
	adminProtected.Use(middleware.AdminAuth(cfg.Auth.JWTSecret))
	adminProtected.POST("/uploads/images", server.uploadImage)
	adminProtected.GET("/:resource", server.listResources)
	adminProtected.POST("/:resource", server.createResource)
	adminProtected.GET("/:resource/:publicId", server.getResource)
	adminProtected.PUT("/:resource/:publicId", server.updateResource)
	adminProtected.DELETE("/:resource/:publicId", server.deleteResource)
	adminProtected.POST("/:resource/:publicId/publish", server.publishResource)
	adminProtected.POST("/:resource/:publicId/unpublish", server.unpublishResource)

	syncGroup := router.Group("/api/v1/sync")
	syncGroup.GET("/snapshot", server.snapshot)
	syncGroup.GET("/changes", server.changes)

	return router
}
