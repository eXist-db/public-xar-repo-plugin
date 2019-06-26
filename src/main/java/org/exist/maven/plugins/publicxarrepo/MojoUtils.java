package org.exist.maven.plugins.publicxarrepo;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

public class MojoUtils {

    public static List<Proxy> getProxies(final MavenSession mavenSession, final SettingsDecrypter decrypter) {
        if (mavenSession == null ||
                mavenSession.getSettings() == null ||
                mavenSession.getSettings().getProxies() == null ||
                mavenSession.getSettings().getProxies().isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<Proxy> proxies = new ArrayList<>();
            for (Proxy proxy : mavenSession.getSettings().getProxies()) {
                if (proxy.isActive()) {
                    proxy = decryptProxy(proxy, decrypter);
                    proxies.add(proxy);
                }
            }
            return proxies;
        }
    }

    private static Proxy decryptProxy(final Proxy proxy, final SettingsDecrypter decrypter) {
        final DefaultSettingsDecryptionRequest decryptionRequest = new DefaultSettingsDecryptionRequest(proxy);
        final SettingsDecryptionResult decryptedResult = decrypter.decrypt(decryptionRequest);
        return decryptedResult.getProxy();
    }

    public static @Nullable Proxy getProxyForUrl(final List<Proxy> proxies, final String requestUrl) {
        if (proxies.isEmpty()) {
            return null;
        }

        final URI uri = URI.create(requestUrl);
        for (Proxy proxy : proxies) {
            if (!isNonProxyHost(proxy, uri.getHost())) {
                return proxy;
            }
        }

        return null;
    }

    private static boolean isNonProxyHost(final Proxy proxy, final String host) {
        final String nonProxyHosts  = proxy.getNonProxyHosts();
        if (host != null && nonProxyHosts != null && nonProxyHosts.length() > 0) {
            for (final StringTokenizer tokenizer = new StringTokenizer(nonProxyHosts, "|"); tokenizer.hasMoreTokens(); ) {
                String pattern = tokenizer.nextToken();
                pattern = pattern.replace(".", "\\.").replace("*", ".*");
                if (host.matches(pattern)) {
                    return true;
                }
            }
        }

        return false;
    }
}
