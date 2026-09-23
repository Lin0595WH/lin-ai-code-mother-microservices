package com.lin.linaicodeapp.core;

import com.lin.linaicodemother.ai.AiCodeGeneratorService;
import com.lin.linaicodeapp.ai.AiCodeGeneratorServiceFactory;
import com.lin.linaicodeapp.core.builder.VueProjectBuilder;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCodeGeneratorFacadeTest {
    @Test
    void startsVueGenerationThroughTokenStreamService() {
        AiCodeGeneratorServiceFactory factory = mock(AiCodeGeneratorServiceFactory.class);
        AiCodeGeneratorService service = mock(AiCodeGeneratorService.class);
        TokenStream tokenStream = mock(TokenStream.class);
        when(factory.getAiCodeGeneratorService(1L, CodeGenTypeEnum.VUE_PROJECT)).thenReturn(service);
        when(service.generateVueProjectCodeStream(1L, "prompt")).thenReturn(tokenStream);

        AiCodeGeneratorFacade facade = new AiCodeGeneratorFacade(factory, mock(VueProjectBuilder.class));
        facade.generateAndSaveCodeStream("prompt", CodeGenTypeEnum.VUE_PROJECT, 1L);

        verify(service).generateVueProjectCodeStream(1L, "prompt");
    }
}
