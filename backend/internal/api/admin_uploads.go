package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/lolita/app/backend/internal/service"
)

func (s *Server) uploadImage(c *gin.Context) {
	file, err := c.FormFile("file")
	if err != nil {
		respondError(c, service.BadRequest("file is required"))
		return
	}

	url, err := s.uploadService.SaveImage(file)
	if err != nil {
		respondError(c, err)
		return
	}

	respondSuccess(c, http.StatusCreated, gin.H{
		"url": url,
	})
}
