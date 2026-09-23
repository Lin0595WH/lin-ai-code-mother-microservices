package com.lin.linaicodeapp.core.builder;

import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VueProjectBuilderTest {
    @Test
    void rejectsProjectBuildsWhileVueGenerationIsDisabled() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new VueProjectBuilder().buildProject("/tmp/vue-project"));

        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), exception.getCode());
    }
}
