package eu.xenit.contentgrid.slingshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
public class SlingshotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SlingshotApplication.class, args);
    }
}
