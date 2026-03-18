package service

import (
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/lolita/app/backend/internal/config"
	"golang.org/x/crypto/bcrypt"
)

type AuthService struct {
	config config.AuthConfig
}

func NewAuthService(cfg config.AuthConfig) *AuthService {
	return &AuthService{config: cfg}
}

func (s *AuthService) Login(password string) (string, error) {
	if err := bcrypt.CompareHashAndPassword([]byte(s.config.AdminPasswordHash), []byte(password)); err != nil {
		return "", Unauthorized("invalid admin credentials")
	}

	now := time.Now()
	claims := jwt.MapClaims{
		"sub": "admin",
		"iat": now.Unix(),
		"exp": now.Add(time.Duration(s.config.TokenTTLHours) * time.Hour).Unix(),
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString([]byte(s.config.JWTSecret))
	if err != nil {
		return "", err
	}
	return signed, nil
}
