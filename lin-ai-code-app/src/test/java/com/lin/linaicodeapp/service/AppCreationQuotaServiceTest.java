package com.lin.linaicodeapp.service;

import com.lin.linaicodeapp.mapper.AppMapper;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import com.lin.linaicodemother.model.enums.UserRoleEnum;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppCreationQuotaServiceTest {

    private AppMapper appMapper;

    private RLock lock;

    private RedissonClient redissonClient;

    private AppCreationQuotaService quotaService;

    @BeforeEach
    void setUp() {
        appMapper = mock(AppMapper.class);
        lock = mock(RLock.class);
        redissonClient = mock(RedissonClient.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        quotaService = new AppCreationQuotaService(appMapper, redissonClient);
    }

    @ParameterizedTest
    @EnumSource(CodeGenTypeEnum.class)
    void shouldAllowRegularUserToCreateFirstAppOfEachType(CodeGenTypeEnum codeGenType) {
        App app = createApp(codeGenType);
        User user = createUser(UserRoleEnum.USER);
        when(appMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(appMapper.insertSelective(app)).thenReturn(1);

        boolean result = quotaService.saveApp(app, user);

        assertTrue(result);
        verify(redissonClient).getLock("app:create:quota:1");
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(appMapper).selectCountByQuery(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().toSQL().contains("user_id"));
        assertTrue(queryCaptor.getValue().toSQL().contains("code_gen_type"));
        verify(appMapper).insertSelective(app);
        verify(lock).unlock();
    }

    @ParameterizedTest
    @EnumSource(CodeGenTypeEnum.class)
    void shouldRejectSecondAppOfSameType(CodeGenTypeEnum codeGenType) {
        App app = createApp(codeGenType);
        User user = createUser(UserRoleEnum.USER);
        when(appMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> quotaService.saveApp(app, user));

        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("最多只能创建 1 个"));
        verify(appMapper, never()).insertSelective(any(App.class));
        verify(lock).unlock();
    }

    @Test
    void shouldNotLimitAdminUser() {
        App app = createApp(CodeGenTypeEnum.HTML);
        User admin = createUser(UserRoleEnum.ADMIN);
        when(appMapper.insertSelective(app)).thenReturn(1);

        boolean result = quotaService.saveApp(app, admin);

        assertTrue(result);
        verify(appMapper).insertSelective(app);
        verifyNoInteractions(redissonClient);
    }

    @Test
    void shouldRejectRequestWhenUserCreationLockIsBusy() {
        App app = createApp(CodeGenTypeEnum.HTML);
        User user = createUser(UserRoleEnum.USER);
        when(lock.tryLock()).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> quotaService.saveApp(app, user));

        assertEquals(ErrorCode.TOO_MANY_REQUEST.getCode(), exception.getCode());
        assertEquals("项目正在创建中，请勿重复提交", exception.getMessage());
        verifyNoInteractions(appMapper);
        verify(lock, never()).unlock();
    }

    @Test
    void shouldReturnFalseWhenInsertFails() {
        App app = createApp(CodeGenTypeEnum.VUE_PROJECT);
        User user = createUser(UserRoleEnum.USER);
        when(appMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L);
        when(appMapper.insertSelective(app)).thenReturn(0);

        boolean result = quotaService.saveApp(app, user);

        assertFalse(result);
        verify(lock).unlock();
    }

    private App createApp(CodeGenTypeEnum codeGenType) {
        return App.builder()
                .userId(1L)
                .codeGenType(codeGenType.getValue())
                .build();
    }

    private User createUser(UserRoleEnum userRole) {
        return User.builder()
                .id(1L)
                .userRole(userRole.getValue())
                .build();
    }

}
