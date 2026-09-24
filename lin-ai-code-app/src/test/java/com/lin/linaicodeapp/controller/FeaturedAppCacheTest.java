package com.lin.linaicodeapp.controller;

import com.lin.linaicodeapp.service.AppService;
import com.lin.linaicodeapp.service.ProjectDownloadService;
import com.lin.linaicodemother.common.BaseResponse;
import com.lin.linaicodemother.common.DeleteRequest;
import com.lin.linaicodemother.constant.UserConstant;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.model.dto.app.AppAdminUpdateRequest;
import com.lin.linaicodemother.model.dto.app.AppUpdateRequest;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(FeaturedAppCacheTest.TestConfig.class)
class FeaturedAppCacheTest {
    @Autowired
    private AppController controller;
    @Autowired
    private AppService appService;
    @Autowired
    private CacheManager cacheManager;

    private Cache featured;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        reset(appService);
        when(appService.getById(42L)).thenReturn(App.builder().id(42L).userId(7L).priority(99).build());
        featured = cacheManager.getCache("good_app_page");
        featured.clear();
        featured.put("page-1", "cached first page");
        featured.put("page-2-filtered", "cached filtered page");
        cacheManager.getCache("unrelated").put("keep", "other data");
        User user = new User();
        user.setId(7L);
        request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature", "unfeature", "adminDelete", "ownerDelete", "ownerUpdate"})
    void shouldEvictEveryFeaturedPageOnlyAfterSuccessfulMutation(String operation) {
        when(appService.updateById(any(App.class))).thenAnswer(invocation -> {
            assertNotNull(featured.get("page-1"));
            return true;
        });
        when(appService.removeById(42L)).thenAnswer(invocation -> {
            assertNotNull(featured.get("page-1"));
            return true;
        });

        assertEquals(Boolean.TRUE, mutate(operation).getData());

        assertNull(featured.get("page-1"));
        assertNull(featured.get("page-2-filtered"));
        assertEquals("other data", cacheManager.getCache("unrelated").get("keep", String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature", "unfeature", "adminDelete", "ownerDelete", "ownerUpdate"})
    void shouldKeepCachedPagesWhenMutationFails(String operation) {
        if (operation.endsWith("Delete")) {
            assertEquals(Boolean.FALSE, mutate(operation).getData());
        } else {
            assertThrows(BusinessException.class, () -> mutate(operation));
        }
        assertNotNull(featured.get("page-1"));
        assertNotNull(featured.get("page-2-filtered"));
    }

    @Test
    void shouldKeepCachedPagesWhenDeletionThrowsOrUserIsUnauthorized() {
        when(appService.removeById(42L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> mutate("adminDelete"));
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        assertThrows(BusinessException.class, () -> mutate("ownerDelete"));
        assertNotNull(featured.get("page-1"));
        assertNotNull(featured.get("page-2-filtered"));
    }

    private BaseResponse<Boolean> mutate(String operation) {
        DeleteRequest delete = new DeleteRequest();
        delete.setId(42L);
        if (operation.equals("adminDelete")) return controller.deleteAppByAdmin(delete);
        if (operation.equals("ownerDelete")) return controller.deleteApp(delete, request);
        if (operation.equals("ownerUpdate")) {
            AppUpdateRequest update = new AppUpdateRequest();
            update.setId(42L);
            update.setAppName("New name");
            return controller.updateApp(update, request);
        }
        AppAdminUpdateRequest update = new AppAdminUpdateRequest();
        update.setId(42L);
        update.setPriority(operation.equals("feature") ? 99 : 0);
        return controller.updateAppByAdmin(update);
    }

    @Configuration
    @EnableCaching
    @Import(AppController.class)
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("good_app_page", "unrelated");
        }

        @Bean
        AppService appService() {
            return mock(AppService.class);
        }

        @Bean
        ProjectDownloadService projectDownloadService() {
            return mock(ProjectDownloadService.class);
        }

        @Bean
        StringRedisTemplate redisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
