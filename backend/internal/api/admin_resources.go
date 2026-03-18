package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/lolita/app/backend/internal/model"
	"github.com/lolita/app/backend/internal/service"
)

var resourceRouteMap = map[string]model.ResourceType{
	"brands":          model.ResourceBrands,
	"categories":      model.ResourceCategories,
	"styles":          model.ResourceStyles,
	"seasons":         model.ResourceSeasons,
	"sources":         model.ResourceSources,
	"catalog-entries": model.ResourceCatalogEntries,
	"items":           model.ResourceItems,
	"coordinates":     model.ResourceCoordinates,
	"price-plans":     model.ResourcePricePlans,
}

func parseResource(resource string) (model.ResourceType, error) {
	if parsed, ok := resourceRouteMap[resource]; ok {
		return parsed, nil
	}
	return "", service.NotFound("resource route not found")
}

func bindPayload(c *gin.Context, resource model.ResourceType) (any, error) {
	switch resource {
	case model.ResourceBrands:
		var payload service.BrandUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceCategories:
		var payload service.CategoryUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceStyles:
		var payload service.StyleUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceSeasons:
		var payload service.SeasonUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceSources:
		var payload service.SourceUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceCatalogEntries:
		var payload service.CatalogEntryUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceItems:
		var payload service.SharedItemUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourceCoordinates:
		var payload service.CoordinateUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	case model.ResourcePricePlans:
		var payload service.PricePlanUpsertInput
		if err := c.ShouldBindJSON(&payload); err != nil {
			return nil, service.BadRequest(err.Error())
		}
		return payload, nil
	default:
		return nil, service.BadRequest("unsupported resource type")
	}
}

func (s *Server) listResources(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	data, err := s.resourceService.List(resource)
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}

func (s *Server) getResource(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	data, err := s.resourceService.Get(resource, c.Param("publicId"))
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}

func (s *Server) createResource(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	payload, err := bindPayload(c, resource)
	if err != nil {
		respondError(c, err)
		return
	}

	data, err := s.resourceService.Create(resource, payload)
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusCreated, data)
}

func (s *Server) updateResource(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	payload, err := bindPayload(c, resource)
	if err != nil {
		respondError(c, err)
		return
	}

	data, err := s.resourceService.Update(resource, c.Param("publicId"), payload)
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}

func (s *Server) deleteResource(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	if err := s.resourceService.Delete(resource, c.Param("publicId")); err != nil {
		respondError(c, err)
		return
	}
	respondMessage(c, http.StatusOK, "ok")
}

func (s *Server) publishResource(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	data, err := s.resourceService.Publish(resource, c.Param("publicId"))
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}

func (s *Server) unpublishResource(c *gin.Context) {
	resource, err := parseResource(c.Param("resource"))
	if err != nil {
		respondError(c, err)
		return
	}

	data, err := s.resourceService.Unpublish(resource, c.Param("publicId"))
	if err != nil {
		respondError(c, err)
		return
	}
	respondSuccess(c, http.StatusOK, data)
}
