package kasiKotas.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class PrerenderFilter implements Filter {

    private static final String PRERENDER_TOKEN = "CREdXHcVEDRNttQEgRBQ";
    private static final String PRERENDER_SERVICE_URL = "https://service.prerender.io/";

    private static final List<String> BOT_USER_AGENTS = Arrays.asList(
            "googlebot", "bingbot", "yandex", "duckduckbot", "baiduspider", "facebookexternalhit", "twitterbot", "rogerbot", "linkedinbot", "embedly", "quora link preview", "showyoubot", "outbrain", "pinterest", "slackbot", "vkShare", "W3C_Validator"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String userAgent = req.getHeader("User-Agent");

        if (shouldShowPrerenderedPage(req, userAgent)) {
            try {
                String fullUrl = req.getRequestURL().toString();
                String prerenderUrl = PRERENDER_SERVICE_URL + fullUrl;

                HttpServletResponse res = (HttpServletResponse) response;
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(prerenderUrl).openConnection();
                conn.setRequestProperty("User-Agent", userAgent);
                conn.setRequestProperty("X-Prerender-Token", PRERENDER_TOKEN);
                conn.setDoInput(true);
                conn.setRequestMethod("GET");

                int status = conn.getResponseCode();
                if (status >= 200 && status < 300) {
                    res.setContentType("text/html");
                    res.setStatus(status);
                    try (var in = conn.getInputStream();
                         var out = res.getOutputStream()) {
                        in.transferTo(out);
                    }
                    return;
                }
                // Non-2xx from prerender — fall through to normal request handling
            } catch (Exception e) {
                // Prerender unavailable — fall through to normal request handling
            }
        }
        chain.doFilter(request, response);
    }

    private boolean shouldShowPrerenderedPage(HttpServletRequest req, String userAgent) {
        if (userAgent == null) return false;
        String lowerUserAgent = userAgent.toLowerCase();

        for (String bot : BOT_USER_AGENTS) {
            if (lowerUserAgent.contains(bot)) return true;
        }

        return false;
    }
}
