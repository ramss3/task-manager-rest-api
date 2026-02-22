package task_manager_api.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    public static final String CLAIM_TYP = "typ"; // access or refresh
    public static final String CLAIM_JTI = "jti"; // unique token id

    private final Key jwtSecret;
    private final long accessExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-ms:900000}") long accessExpirationMs,       // 15 min
            @Value("${app.jwt.refresh-expiration-ms:1209600000}") long refreshTokenExpirationMs // 14 days
    ) {
        this.jwtSecret = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpirationMs = accessExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, "access", accessExpirationMs);
    }

    public String generateRefreshToken(Long userId) {
        return generateToken(userId, "refresh", refreshTokenExpirationMs);
    }

    private String generateToken(Long userId, String typ, long ttlMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + ttlMs);

        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim(CLAIM_TYP, typ)
                .claim(CLAIM_JTI, jti)
                .signWith(jwtSecret, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        String subject = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        return Long.parseLong(subject);
    }

    public String getTokenType(String token) {
        Object typ = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get(CLAIM_TYP);
        return typ == null ? null : typ.toString();
    }

    public String getJti(String token) {
        Object jti = Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get(CLAIM_JTI);
        return jti == null ? null : jti.toString();
    }

    public Date getExpiration(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(jwtSecret)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(jwtSecret).build().parseClaimsJws(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }
}
