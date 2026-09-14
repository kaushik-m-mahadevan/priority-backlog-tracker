package com.backlogtracker.backlogtracker.config.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backlogtracker.backlogtracker.config.service.GroupCategoryService;
import com.backlogtracker.commons.security.AuthUser;
import com.backlogtracker.commons.security.RequiresUser;

import lombok.RequiredArgsConstructor;

/**
 * A group's own category list — kept at the same {@code /api/groups/{id}/categories} URL
 * prefix as before this moved out of the commons {@code GroupController}, so the frontend
 * needed no changes; only the owning Java package did.
 */
@RestController
@RequestMapping("/api/groups/{id}/categories")
@RequiresUser
@RequiredArgsConstructor
public class GroupCategoryController {

    private final GroupCategoryService categoryService;

    @GetMapping
    public CategoriesView get(@PathVariable String id, @AuthenticationPrincipal AuthUser actor) {
        return new CategoriesView(categoryService.categoriesFor(id, actor.id()));
    }

    /** Add a category to this group's own list. Any member may. */
    @PostMapping
    public CategoriesView add(@PathVariable String id, @RequestBody NameRequest request,
                              @AuthenticationPrincipal AuthUser actor) {
        return new CategoriesView(categoryService.addCategory(id, actor.id(), request.name()));
    }

    /** Remove a category. 409 if items still use it without a {@code reassignTo}. */
    @DeleteMapping("/{name}")
    public CategoriesView remove(@PathVariable String id, @PathVariable String name,
                                 @RequestParam(required = false) String reassignTo,
                                 @AuthenticationPrincipal AuthUser actor) {
        return new CategoriesView(categoryService.removeCategory(id, actor.id(), name, reassignTo));
    }

    public record CategoriesView(List<String> categories) {
    }

    public record NameRequest(String name) {
    }
}
