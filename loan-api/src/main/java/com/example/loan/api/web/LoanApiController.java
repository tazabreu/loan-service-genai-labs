package com.example.loan.api.web;

import com.example.loan.api.domain.model.Loan;
import com.example.loan.api.domain.service.LoanService;
import com.example.loan.api.web.dto.LoanSimulationRequest;
import com.example.loan.api.web.dto.PaymentRequest;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/loans")
public class LoanApiController {
    private final LoanService service;
    private final Client flags;

    public LoanApiController(LoanService service) {
        this.service = service;
        this.flags = OpenFeatureAPI.getInstance().getClient();
    }

    @PostMapping("/simulate")
    public ResponseEntity<Loan> simulate(@RequestBody @Valid LoanSimulationRequest req) {
        boolean manual = flags.getBooleanValue("manual-approval-enabled", true);
        Loan loan = service.simulate(req.amount(), req.termMonths(), req.interestRate(), req.customerId(), manual);
        return ResponseEntity.created(URI.create("/loans/" + loan.id())).body(loan);
    }

    @PostMapping("/{id}/approve")
    public Loan approve(@PathVariable("id") UUID id, @RequestHeader(name = "X-User", required = false, defaultValue = "backoffice") String user) {
        return service.approve(id, user);
    }

    @PostMapping("/{id}/reject")
    public Loan reject(@PathVariable("id") UUID id, @RequestHeader(name = "X-User", required = false, defaultValue = "backoffice") String user) {
        return service.reject(id, user, "manual review");
    }

    @PostMapping("/{id}/contract")
    public Loan contract(@PathVariable("id") UUID id) { return service.contract(id); }

    @PostMapping("/{id}/disburse")
    public Loan disburse(@PathVariable("id") UUID id) { return service.disburse(id); }

    @PostMapping("/{id}/pay")
    public Loan pay(@PathVariable("id") UUID id, @RequestBody @Valid PaymentRequest req) { return service.pay(id, req.amount()); }

    @GetMapping("/healthz")
    public Map<String, String> health() { return Map.of("status", "ok"); }
}
