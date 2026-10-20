package rw.gov.ptc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PtcApplication {

    public static void main(String[] args) {
        // All instants are UTC; Africa/Kigali is applied only for display and
        // calendar arithmetic (see common.Time).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(PtcApplication.class, args);
    }
}
