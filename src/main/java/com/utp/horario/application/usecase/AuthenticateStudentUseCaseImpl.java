package com.utp.horario.application.usecase;

import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.in.AuthenticateStudentUseCase;
import com.utp.horario.domain.port.out.StudentRepositoryPort;
import com.utp.horario.domain.port.out.TokenSignatureVerifierPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticateStudentUseCaseImpl implements AuthenticateStudentUseCase {

    private final StudentRepositoryPort studentRepositoryPort;
    private final UtpPortalGatewayPort utpPortalGatewayPort;

    /**
     * Verificador de firma del token. Es opcional: si no esta disponible (por ejemplo en
     * pruebas unitarias) el token se inspecciona como antes. Cuando si esta disponible y
     * la verificacion esta activa, un token que no fue emitido por el SSO institucional se
     * rechaza antes de resolver la identidad del estudiante.
     */
    private TokenSignatureVerifierPort tokenSignatureVerifierPort;

    @Autowired(required = false)
    public void setTokenSignatureVerifierPort(TokenSignatureVerifierPort tokenSignatureVerifierPort) {
        this.tokenSignatureVerifierPort = tokenSignatureVerifierPort;
    }

    @Override
    public StudentProfile authenticateWithCredentials(String username, String password) {
        StudentProfile profile = utpPortalGatewayPort.login(username, password);
        return studentRepositoryPort.save(profile);
    }

    /**
     * Resuelve la identidad del estudiante a partir del token Bearer presentado.
     *
     * <p>El token se inspecciona de forma <b>stateless</b>: no se consulta ni se cede
     * ninguna sesión guardada en memoria. Un token ausente, malformado o expirado
     * produce {@link AuthenticationException} (HTTP 401).</p>
     */
    @Override
    public StudentProfile authenticateWithToken(String token) {
        if (tokenSignatureVerifierPort != null
                && tokenSignatureVerifierPort.check(token) == TokenSignatureVerifierPort.Status.INVALID) {
            throw new AuthenticationException(
                    "El token de sesion no fue emitido por el SSO institucional o su firma no es valida.");
        }
        SessionTokenInspector.SessionClaims claims = SessionTokenInspector.inspect(token);
        String studentCode = claims.studentCode();

        StudentProfile profile = StudentProfile.builder()
                .id(claims.userId())
                .studentCode(studentCode)
                .fullName(claims.fullName())
                .email(claims.email())
                .career(claims.career())
                .campus(claims.campus())
                .currentCycle(claims.cycle())
                .token(SessionTokenInspector.normalizeToken(token))
                .enrolledCourseCodes(List.of())
                .build();

        return studentRepositoryPort.findByStudentCode(studentCode)
                .filter(existing -> existing.getId() != null && !existing.getId().isBlank())
                .map(existing -> {
                    existing.setToken(profile.getToken());
                    return studentRepositoryPort.save(existing);
                })
                .orElseGet(() -> studentRepositoryPort.save(profile));
    }

    @Override
    public StudentProfile refreshToken(String refreshToken) {
        StudentProfile profile = utpPortalGatewayPort.refreshToken(refreshToken);
        return studentRepositoryPort.save(profile);
    }

    @Override
    public StudentProfile getProfile(String studentId) {
        return studentRepositoryPort.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado: " + studentId));
    }
}
