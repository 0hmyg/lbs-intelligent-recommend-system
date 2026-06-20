package org.example.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey secretKey;
    private final long expiresSeconds;

    public JwtUtil(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expires-seconds}") long expiresSeconds
    ) {
        // JJWT 对 key 长度有要求，太短会抛异常；这里用字节填充保证可用（生产环境请用足够长的随机 secret）
        byte[] raw = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = raw.length >= 32 ? raw : padTo(raw, 32);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expiresSeconds = expiresSeconds;
    }

    private static byte[] padTo(byte[] src, int len) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = src.length == 0 ? (byte) '0' : src[i % src.length];
        }
        return out;
    }

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiresSeconds * 1000);
        return Jwts.builder()
                .setSubject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("role", principal.getRole())
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

