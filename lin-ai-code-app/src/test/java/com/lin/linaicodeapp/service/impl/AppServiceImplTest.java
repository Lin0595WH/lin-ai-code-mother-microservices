package com.lin.linaicodeapp.service.impl;

import com.lin.linaicodeapp.ai.AiCodeGenTypeRoutingServiceFactory;
import com.lin.linaicodeapp.core.AiCodeGeneratorFacade;
import com.lin.linaicodeapp.core.handler.StreamHandlerExecutor;
import com.lin.linaicodeapp.service.AppCreationQuotaService;
import com.lin.linaicodeapp.service.ChatHistoryService;
import com.lin.linaicodemother.ai.AiCodeGenTypeRoutingService;
import com.lin.linaicodemother.ai.model.RoutingResult;
import com.lin.linaicodemother.model.dto.app.AppAddRequest;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppServiceImplTest {

    @Test
    void shouldPreserveVueTypeWhenCreatingApp() {
        AiCodeGenTypeRoutingServiceFactory routingFactory = mock(AiCodeGenTypeRoutingServiceFactory.class);
        AiCodeGenTypeRoutingService routingService = mock(AiCodeGenTypeRoutingService.class);
        AppCreationQuotaService quotaService = mock(AppCreationQuotaService.class);
        RoutingResult routingResult = new RoutingResult();
        routingResult.setCodeGenTypeEnum(CodeGenTypeEnum.VUE_PROJECT);
        when(routingFactory.createAiCodeGenTypeRoutingService()).thenReturn(routingService);
        when(routingService.routing("Build a Vue dashboard")).thenReturn(routingResult);
        when(quotaService.saveApp(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        AppServiceImpl service = new AppServiceImpl(mock(com.lin.linaicodemother.mapstruct.AppModuleMapper.class),
                mock(AiCodeGeneratorFacade.class), mock(ChatHistoryService.class),
                mock(StreamHandlerExecutor.class), routingFactory, quotaService);
        AppAddRequest request = new AppAddRequest();
        request.setInitPrompt("Build a Vue dashboard");
        User user = new User();
        user.setId(1L);

        service.createApp(request, user);

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(quotaService).saveApp(appCaptor.capture(), org.mockito.ArgumentMatchers.eq(user));
        assertEquals(CodeGenTypeEnum.VUE_PROJECT.getValue(), appCaptor.getValue().getCodeGenType());
    }
}
