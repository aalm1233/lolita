package api

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/lolita/app/backend/internal/service"
)

type responseEnvelope struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

func respondSuccess(c *gin.Context, status int, data any) {
	c.JSON(status, responseEnvelope{
		Code:    status,
		Message: "ok",
		Data:    data,
	})
}

func respondMessage(c *gin.Context, status int, message string) {
	c.JSON(status, responseEnvelope{
		Code:    status,
		Message: message,
	})
}

func respondError(c *gin.Context, err error) {
	if appErr, ok := err.(*service.AppError); ok {
		c.JSON(appErr.StatusCode, responseEnvelope{
			Code:    appErr.StatusCode,
			Message: appErr.Message,
		})
		return
	}

	c.JSON(http.StatusInternalServerError, responseEnvelope{
		Code:    http.StatusInternalServerError,
		Message: "internal server error",
	})
}

func trimBaseURL(value string) string {
	return strings.TrimRight(strings.TrimSpace(value), "/")
}
