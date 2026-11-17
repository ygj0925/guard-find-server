package com.guardfind.server.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/accounting/snapshot")
public class AccountingSnapshotController {
    private final AccountingSnapshotService service;
    public record SaveReq(@PositiveOrZero long baseVersion, @NotBlank @Size(max = 64) String operationId,
                          @NotBlank @Size(max = 5000000) String payload) {}
    @GetMapping
    public AccountingSnapshotService.Result get(@RequestParam(required = false) Long knownVersion) {
        var result = service.get();
        return knownVersion != null && result.version() == knownVersion ? new AccountingSnapshotService.Result(result.version(), null) : result;
    }
    public record Change(@NotBlank String collection, @NotBlank String key, JsonNode value) {}
    public record PatchReq(@PositiveOrZero long baseVersion, @NotBlank @Size(max = 64) String operationId,
                           @Size(min = 1, max = 1000) @jakarta.validation.constraints.NotNull List<@Valid Change> changes) {}
    @PatchMapping
    public AccountingSnapshotService.Result patch(@Valid @RequestBody PatchReq req) {
        return service.patch(req.baseVersion(), req.operationId(), req.changes());
    }
    @PutMapping
    public AccountingSnapshotService.Result save(@Valid @RequestBody SaveReq req) {
        return service.save(req.baseVersion(), req.operationId(), req.payload());
    }
}
