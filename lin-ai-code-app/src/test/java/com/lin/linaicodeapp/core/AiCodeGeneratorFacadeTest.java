package com.lin.linaicodeapp.core;

import com.lin.linaicodeapp.ai.AiCodeGeneratorServiceFactory;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiCodeGeneratorFacadeTest {
    @Test
    void rejectsVueGenerationBeforeAccessingAiService() {
        AiCodeGeneratorServiceFactory factory = mock(AiCodeGeneratorServiceFactory.class);
        AiCodeGeneratorFacade facade = new AiCodeGeneratorFacade(factory);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> facade.generateAndSaveCodeStream("prompt", CodeGenTypeEnum.VUE_PROJECT, 1L));

        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(factory);
    }
}
