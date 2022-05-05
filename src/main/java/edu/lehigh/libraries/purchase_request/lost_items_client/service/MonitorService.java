package edu.lehigh.libraries.purchase_request.lost_items_client.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MonitorService {

    public MonitorService() {
        log.info("Starting MonitorService.");
    }
    
    @Scheduled(cron = "${lost-items-client.schedule}")
    public void checkForLostItems() {
        log.info("Schedule triggered: checking for lost items.");
    }

}
