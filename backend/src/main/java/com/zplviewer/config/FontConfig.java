package com.zplviewer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.awt.Font;
import java.io.InputStream;

@Configuration
public class FontConfig {

    @Bean
    public Font cgTriumvirateFont() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fonts/BarlowCondensed-Bold.ttf")) {
            if (is == null) {
                throw new IllegalStateException(
                        "字型檔案不存在：classpath:/fonts/BarlowCondensed-Bold.ttf\n"
                        + "請將 BarlowCondensed-Bold.ttf 放置到 src/main/resources/fonts/");
            }
            return Font.createFont(Font.TRUETYPE_FONT, is);
        }
    }
}
