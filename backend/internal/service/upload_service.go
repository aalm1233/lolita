package service

import (
	"fmt"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/uuid"
	"github.com/lolita/app/backend/internal/config"
)

type UploadService struct {
	config config.UploadConfig
}

func NewUploadService(cfg config.UploadConfig) *UploadService {
	return &UploadService{config: cfg}
}

func (s *UploadService) SaveImage(file *multipart.FileHeader) (string, error) {
	if file == nil {
		return "", BadRequest("file is required")
	}
	if file.Size <= 0 {
		return "", BadRequest("file must not be empty")
	}
	if file.Size > s.config.MaxSizeBytes {
		return "", BadRequest("file exceeds the configured size limit")
	}

	ext := strings.ToLower(strings.TrimPrefix(filepath.Ext(file.Filename), "."))
	if !s.isAllowedExtension(ext) {
		return "", BadRequest("only jpg, jpeg, png, and webp files are allowed")
	}

	filename := fmt.Sprintf("%s.%s", uuid.NewString(), ext)
	targetPath := filepath.Join(s.config.Path, filename)
	if err := os.MkdirAll(s.config.Path, 0o755); err != nil {
		return "", err
	}
	if err := saveUploadedFile(file, targetPath); err != nil {
		return "", err
	}

	return "/uploads/images/" + filename, nil
}

func (s *UploadService) isAllowedExtension(ext string) bool {
	for _, allowed := range s.config.AllowedTypes {
		if ext == allowed {
			return true
		}
	}
	return false
}

func saveUploadedFile(file *multipart.FileHeader, targetPath string) error {
	src, err := file.Open()
	if err != nil {
		return err
	}
	defer src.Close()

	dst, err := os.Create(targetPath)
	if err != nil {
		return err
	}
	defer dst.Close()

	_, err = dst.ReadFrom(src)
	return err
}
