package config

import (
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Server   ServerConfig   `yaml:"server"`
	Database DatabaseConfig `yaml:"database"`
	Auth     AuthConfig     `yaml:"auth"`
	Upload   UploadConfig   `yaml:"upload"`
}

type ServerConfig struct {
	Addr          string `yaml:"addr"`
	Mode          string `yaml:"mode"`
	PublicBaseURL string `yaml:"public_base_url"`
}

type DatabaseConfig struct {
	Path string `yaml:"path"`
}

type AuthConfig struct {
	JWTSecret         string `yaml:"jwt_secret"`
	AdminPasswordHash string `yaml:"admin_password_hash"`
	TokenTTLHours     int    `yaml:"token_ttl_hours"`
}

type UploadConfig struct {
	Path         string   `yaml:"path"`
	MaxSizeBytes int64    `yaml:"max_size_bytes"`
	AllowedTypes []string `yaml:"allowed_types"`
}

func Default() Config {
	return Config{
		Server: ServerConfig{
			Addr:          ":8080",
			Mode:          "debug",
			PublicBaseURL: "http://localhost:8080",
		},
		Database: DatabaseConfig{
			Path: "./data/lolita-backend.db",
		},
		Auth: AuthConfig{
			TokenTTLHours: 168,
		},
		Upload: UploadConfig{
			Path:         "./data/uploads/images",
			MaxSizeBytes: 10 * 1024 * 1024,
			AllowedTypes: []string{"jpg", "jpeg", "png", "webp"},
		},
	}
}

func Load(path string) (Config, error) {
	cfg := Default()

	if path != "" {
		if data, err := os.ReadFile(path); err == nil {
			if err := yaml.Unmarshal(data, &cfg); err != nil {
				return Config{}, err
			}
		} else if !errors.Is(err, os.ErrNotExist) {
			return Config{}, err
		}
	}

	applyEnvOverrides(&cfg)
	normalize(&cfg)
	if err := validate(cfg); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func (c Config) EnsureRuntimePaths() error {
	if err := os.MkdirAll(filepath.Dir(c.Database.Path), 0o755); err != nil {
		return err
	}
	return os.MkdirAll(c.Upload.Path, 0o755)
}

func applyEnvOverrides(cfg *Config) {
	overrideString("LOLITA_BACKEND_SERVER_ADDR", &cfg.Server.Addr)
	overrideString("LOLITA_BACKEND_SERVER_MODE", &cfg.Server.Mode)
	overrideString("LOLITA_BACKEND_PUBLIC_BASE_URL", &cfg.Server.PublicBaseURL)
	overrideString("LOLITA_BACKEND_DATABASE_PATH", &cfg.Database.Path)
	overrideString("LOLITA_BACKEND_JWT_SECRET", &cfg.Auth.JWTSecret)
	overrideString("LOLITA_BACKEND_ADMIN_PASSWORD_HASH", &cfg.Auth.AdminPasswordHash)
	overrideInt("LOLITA_BACKEND_TOKEN_TTL_HOURS", &cfg.Auth.TokenTTLHours)
	overrideString("LOLITA_BACKEND_UPLOAD_PATH", &cfg.Upload.Path)
	overrideInt64("LOLITA_BACKEND_UPLOAD_MAX_SIZE", &cfg.Upload.MaxSizeBytes)
	if value := strings.TrimSpace(os.Getenv("LOLITA_BACKEND_UPLOAD_ALLOWED_TYPES")); value != "" {
		parts := strings.Split(value, ",")
		cfg.Upload.AllowedTypes = cfg.Upload.AllowedTypes[:0]
		for _, part := range parts {
			if trimmed := strings.ToLower(strings.TrimSpace(part)); trimmed != "" {
				cfg.Upload.AllowedTypes = append(cfg.Upload.AllowedTypes, trimmed)
			}
		}
	}
}

func normalize(cfg *Config) {
	cfg.Server.Mode = strings.ToLower(strings.TrimSpace(cfg.Server.Mode))
	if cfg.Server.Mode == "" {
		cfg.Server.Mode = "debug"
	}
	cfg.Upload.Path = filepath.Clean(cfg.Upload.Path)
	cfg.Database.Path = filepath.Clean(cfg.Database.Path)
	seen := make(map[string]struct{})
	filtered := make([]string, 0, len(cfg.Upload.AllowedTypes))
	for _, ext := range cfg.Upload.AllowedTypes {
		trimmed := strings.ToLower(strings.TrimPrefix(strings.TrimSpace(ext), "."))
		if trimmed == "" {
			continue
		}
		if _, ok := seen[trimmed]; ok {
			continue
		}
		seen[trimmed] = struct{}{}
		filtered = append(filtered, trimmed)
	}
	if len(filtered) > 0 {
		cfg.Upload.AllowedTypes = filtered
	}
}

func validate(cfg Config) error {
	if cfg.Auth.JWTSecret == "" {
		return errors.New("auth.jwt_secret is required")
	}
	if cfg.Auth.AdminPasswordHash == "" {
		return errors.New("auth.admin_password_hash is required")
	}
	if cfg.Upload.Path == "" {
		return errors.New("upload.path is required")
	}
	if cfg.Database.Path == "" {
		return errors.New("database.path is required")
	}
	if cfg.Upload.MaxSizeBytes <= 0 {
		return errors.New("upload.max_size_bytes must be greater than 0")
	}
	if len(cfg.Upload.AllowedTypes) == 0 {
		return errors.New("upload.allowed_types must not be empty")
	}
	return nil
}

func overrideString(name string, target *string) {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		*target = value
	}
}

func overrideInt(name string, target *int) {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		if parsed, err := strconv.Atoi(value); err == nil {
			*target = parsed
		}
	}
}

func overrideInt64(name string, target *int64) {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		if parsed, err := strconv.ParseInt(value, 10, 64); err == nil {
			*target = parsed
		}
	}
}
