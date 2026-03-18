package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/lolita/app/backend/internal/service"
)

type loginRequest struct {
	Password string `json:"password" binding:"required"`
}

func (s *Server) login(c *gin.Context) {
	var request loginRequest
	if err := c.ShouldBindJSON(&request); err != nil {
		respondError(c, service.BadRequest(err.Error()))
		return
	}

	token, err := s.authService.Login(request.Password)
	if err != nil {
		respondError(c, err)
		return
	}

	respondSuccess(c, http.StatusOK, gin.H{
		"token":            token,
		"tokenType":        "Bearer",
		"expiresInSeconds": s.config.Auth.TokenTTLHours * 3600,
	})
}
