package com.mediledger.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Service for JWT token generation and validation
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationTime;

    /**
     * Generates a JWT token for a user
     */
    public String generateToken(String username, String role) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);

            return JWT.create()
                    .withSubject(username)
                    .withClaim("role", role)
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(System.currentTimeMillis() + expirationTime))
                    .sign(algorithm);
        } catch (Exception e) {
            System.err.println("Error generating JWT token: " + e.getMessage());
            throw new RuntimeException("Could not generate token", e);
        }
    }

    /**
     * Validates a JWT token and returns the decoded JWT
     */
    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            System.err.println("Invalid JWT token: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Extracts username from JWT token
     */
    public String extractUsername(String token) {
        DecodedJWT decodedJWT = validateToken(token);
        return decodedJWT.getSubject();
    }

    /**
     * Extracts role from JWT token
     */
    public String extractRole(String token) {
        DecodedJWT decodedJWT = validateToken(token);
        return decodedJWT.getClaim("role").asString();
    }

    /**
     * Checks if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            DecodedJWT decodedJWT = validateToken(token);
            return decodedJWT.getExpiresAt().before(new Date());
        } catch (JWTVerificationException e) {
            return true;
        }
    }
}
