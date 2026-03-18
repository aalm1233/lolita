package api

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/lolita/app/backend/internal/service"
)

func (s *Server) snapshot(c *gin.Context) {
	data, err := s.syncService.Snapshot(s.assetBaseURL(c))
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}

func (s *Server) changes(c *gin.Context) {
	cursor := uint64(0)
	if raw := c.Query("cursor"); raw != "" {
		parsed, err := strconv.ParseUint(raw, 10, 64)
		if err != nil {
			respondError(c, service.BadRequest("cursor must be an unsigned integer"))
			return
		}
		cursor = parsed
	}

	limit := 200
	if raw := c.Query("limit"); raw != "" {
		parsed, err := strconv.Atoi(raw)
		if err != nil {
			respondError(c, service.BadRequest("limit must be an integer"))
			return
		}
		limit = parsed
	}
	if limit < 1 {
		limit = 1
	}
	if limit > 500 {
		limit = 500
	}

	data, err := s.syncService.Changes(s.assetBaseURL(c), cursor, limit)
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}

func (s *Server) assetBaseURL(c *gin.Context) string {
	if baseURL := trimBaseURL(s.config.Server.PublicBaseURL); baseURL != "" {
		return baseURL
	}

	scheme := "http"
	if forwarded := c.GetHeader("X-Forwarded-Proto"); forwarded != "" {
		scheme = forwarded
	} else if c.Request.TLS != nil {
		scheme = "https"
	}
	return scheme + "://" + c.Request.Host
}
