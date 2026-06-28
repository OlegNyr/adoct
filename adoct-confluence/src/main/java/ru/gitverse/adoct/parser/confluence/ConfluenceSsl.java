package ru.gitverse.adoct.parser.confluence;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


public class ConfluenceSsl {

    public static final TrustManager NOT_VERIFY_SSL = new YesTrustManager();
    public static final HostnameVerifier NOT_VERIFY_HOST = new YesHostnameVerifier();

    public static SSLContext newInstance(final TrustManager trustManager) {
        try {
            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{trustManager}, null);
            return ctx;
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static class YesHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(final String s, final SSLSession sslSession) {
            return true;
        }

    }

    public static class YesTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(final X509Certificate[] x509Certificates, final String s) throws CertificateException {
            // no-op
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] x509Certificates, final String s) throws CertificateException {
            // no-op
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }
}
