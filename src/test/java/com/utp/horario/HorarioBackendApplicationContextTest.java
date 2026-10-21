package com.utp.horario;

import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.application.usecase.AuthenticateStudentUseCaseImpl;
import com.utp.horario.application.usecase.ScheduleServiceImpl;
import com.utp.horario.application.usecase.SyllabusServiceImpl;
import com.utp.horario.infrastructure.external.UtpPortalGatewayAdapter;
import com.utp.horario.infrastructure.persistence.adapter.ScheduleRepositoryAdapter;
import com.utp.horario.infrastructure.persistence.adapter.SyllabusRepositoryAdapter;
import com.utp.horario.presentation.controller.AuthController;
import com.utp.horario.presentation.controller.CourseSummaryController;
import com.utp.horario.presentation.controller.RootController;
import com.utp.horario.presentation.controller.ScheduleController;
import com.utp.horario.presentation.controller.SyllabusController;
import com.utp.horario.presentation.controller.TaskController;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba de humo del contexto de aplicación.
 *
 * <p>El resto de la suite utiliza {@code MockMvcBuilders.standaloneSetup}, por lo que el
 * contexto de Spring completo nunca se instancia en las pruebas. Esta prueba garantiza
 * que todos los beans (controladores, adaptadores, manejo global de excepciones) se
 * enlazan correctamente y que el arranque de la aplicación no falla.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HorarioBackendApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("El contexto de Spring arranca y expone todos los controladores y el manejador global de errores")
    void contextLoadsWithAllControllersAndAdvice() {
        assertNotNull(applicationContext.getBean(AuthController.class));
        assertNotNull(applicationContext.getBean(SyllabusController.class));
        assertNotNull(applicationContext.getBean(ScheduleController.class));
        assertNotNull(applicationContext.getBean(TaskController.class));
        assertNotNull(applicationContext.getBean(CourseSummaryController.class));
        assertNotNull(applicationContext.getBean(RootController.class));

        assertNotNull(applicationContext.getBean(GlobalExceptionHandler.class));

        assertNotNull(applicationContext.getBean(AuthenticateStudentUseCaseImpl.class));
        assertNotNull(applicationContext.getBean(SyllabusServiceImpl.class));
        assertNotNull(applicationContext.getBean(ScheduleServiceImpl.class));

        assertNotNull(applicationContext.getBean(UtpPortalGatewayAdapter.class));
        assertNotNull(applicationContext.getBean(SyllabusRepositoryAdapter.class));
        assertNotNull(applicationContext.getBean(ScheduleRepositoryAdapter.class));

        assertNotNull(SessionTokenInspector.class);
    }
}
