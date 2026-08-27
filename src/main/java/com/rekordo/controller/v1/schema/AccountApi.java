package com.rekordo.controller.v1.schema;

import com.rekordo.model.core.AccountExportDto;
import com.rekordo.model.core.ConsentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * The DSGVO self-service endpoints (design turn 17).
 *
 * <p>They exist so that nothing here has to go through an e-mail to a person who then does it
 * by hand: Art. 12 Abs. 3 gives a month to answer, and a month is the wrong answer to
 * "what do you have about me". Rectification and erasure are already on {@code /auth/me} and
 * the Sharing screen, so what is left here is access, portability and the consent record.
 */
@RequestMapping("/api/v1/account")
@Tag(name = "Account")
public interface AccountApi {

    @GetMapping("/consents")
    @Operation(
            summary = "What this account agreed to, and when",
            description = "Newest first. The version is the one the document carried at the moment "
                    + "of acceptance, not the one it carries now.")
    @ApiResponse(responseCode = "200", description = "The consent record")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<List<ConsentDto>> consents();

    @GetMapping("/export")
    @Operation(
            summary = "Everything the server holds about this account",
            description = "The Art. 15 and Art. 20 answer in one JSON file: account, consents, "
                    + "sharing settings, copies, wishlist, photo metadata and friendships. Photo "
                    + "bytes are not inlined -- each photo's metadata carries the URL they live at. "
                    + "A device with no account has nothing here and exports from its own store "
                    + "instead.")
    @ApiResponse(responseCode = "200", description = "The export, as a file download")
    @ApiResponse(responseCode = "401", description = "Not signed in")
    ResponseEntity<AccountExportDto> export();
}
