package com.musiccollector.controller.v1.implementation;

import com.musiccollector.controller.v1.schema.AccountApi;
import com.musiccollector.model.core.AccountExportDto;
import com.musiccollector.model.core.ConsentDto;
import com.musiccollector.security.CurrentUser;
import com.musiccollector.services.account.AccountExportService;
import com.musiccollector.services.auth.ConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountController implements AccountApi {

    private final ConsentService consentService;
    private final AccountExportService accountExportService;
    private final CurrentUser currentUser;

    @Override
    public ResponseEntity<List<ConsentDto>> consents() {
        return ResponseEntity.ok(consentService.list(currentUser.require().getId()));
    }

    @Override
    public ResponseEntity<AccountExportDto> export() {
        AccountExportDto body = accountExportService.export(currentUser.require());
        // Named and marked as an attachment here rather than in the client: a browser that
        // opens the file in a tab has technically delivered it, and nobody keeping a copy
        // of their data wants to save it out of a JSON viewer.
        String filename = "music-collector-export-%s.json"
                .formatted(LocalDate.ofInstant(body.exportedAt(), ZoneOffset.UTC));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
