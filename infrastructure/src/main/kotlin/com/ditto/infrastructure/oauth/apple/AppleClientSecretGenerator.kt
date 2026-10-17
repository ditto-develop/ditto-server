package com.ditto.infrastructure.oauth.apple

import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 애플 auth/token·auth/revoke 에 보내는 client_secret 을 만든다. 팀의 .p8 키로 ES256 서명한 JWT 다.
 * 애플은 최대 6개월까지 허용하지만 요청마다 새로 만들어 짧게 쓴다.
 */
class AppleClientSecretGenerator(
    private val properties: AppleOAuthProperties,
) {
    private val privateKey: PrivateKey by lazy { readPrivateKey(properties.privateKey) }

    fun generate(clientId: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .header().keyId(properties.keyId).and()
            .issuer(properties.teamId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(LIFETIME)))
            .audience().add(AppleOAuthProperties.ISSUER).and()
            .subject(clientId)
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
    }

    // .p8 은 PKCS#8 PEM 이다. 환경변수로 넣으면서 줄바꿈이 사라지거나 \n 으로 바뀌어도 읽을 수 있게 머리·꼬리·공백을 걷어낸다.
    private fun readPrivateKey(pem: String): PrivateKey {
        val base64 = pem
            .replace("\\n", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot(Char::isWhitespace)
        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64))
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    companion object {
        private val LIFETIME = Duration.ofMinutes(5)
    }
}
