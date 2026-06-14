package com.canton.platform.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.canton.platform.domain.contracts.ExecutedTrade;
import com.canton.platform.domain.contracts.TradeProposal;
import com.canton.platform.dto.request.TradeCancelRequest;
import com.canton.platform.dto.request.TradeProposeRequest;
import com.canton.platform.dto.request.TradeRejectRequest;
import com.canton.platform.dto.request.TradeSettleRequest;
import com.canton.platform.service.TradeService;
import com.canton.platform.web.CommandExecutor;
import com.canton.platform.web.Correlation;

/**
 * Bilateral DvP trade lifecycle endpoints. Mirrors DAML templates
 * {@code Trading.Trade.BondTradeProposal} / {@code BillTradeProposal} and
 * their choices: {@code propose -> AcceptAndSettle | Reject | Cancel}.
 * Settlement ({@code /settle}) is atomic -- asset and cash legs transfer in
 * one synchronized ledger operation, and on success an
 * {@code ExecutedTrade} is created and a {@code trade.executed} event is
 * published for downstream settlement handlers.
 */
@RestController
@RequestMapping("/api/trades")
@Tag(name = "Trades", description = "Delivery-vs-Payment (DvP) bond/bill trading")
public class TradeController {

    private final TradeService tradeService;
    private final CommandExecutor commandExecutor;

    public TradeController(TradeService tradeService, CommandExecutor commandExecutor) {
        this.tradeService = tradeService;
        this.commandExecutor = commandExecutor;
    }

    @Operation(summary = "Seller proposes a DvP trade")
    @PostMapping("/propose")
    public ResponseEntity<TradeProposal> propose(@RequestBody @Valid TradeProposeRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TradeProposal result = commandExecutor.submit(idempotencyKey, req, "trade.propose:" + req.tradeId(), () ->
                tradeService.proposeTrade(req.seller(), req.buyer(), req.regulator(), req.tradeId(),
                        req.assetClass(), req.instrumentId(), req.quantity(), req.price()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Buyer accepts and atomically settles the trade (DvP)")
    @PostMapping("/{tradeId}/settle")
    public ResponseEntity<ExecutedTrade> settle(@PathVariable String tradeId,
                                                  @RequestBody @Valid TradeSettleRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        ExecutedTrade result = commandExecutor.submit(idempotencyKey, req, "trade.settle:" + tradeId, () ->
                tradeService.acceptAndSettle(tradeId, req.buyer(), req.sellerWalletId(), req.buyerWalletId(),
                        req.settledInstrumentId(), req.settledDepositId(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Buyer rejects the trade proposal")
    @PostMapping("/{tradeId}/reject")
    public ResponseEntity<TradeProposal> reject(@PathVariable String tradeId,
                                                 @RequestBody @Valid TradeRejectRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TradeProposal result = commandExecutor.submit(idempotencyKey, req, "trade.reject:" + tradeId, () ->
                tradeService.reject(tradeId, req.buyer(), req.reason()));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Seller cancels the trade proposal before acceptance")
    @PostMapping("/{tradeId}/cancel")
    public ResponseEntity<TradeProposal> cancel(@PathVariable String tradeId,
                                                 @RequestBody @Valid TradeCancelRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TradeProposal result = commandExecutor.submit(idempotencyKey, req, "trade.cancel:" + tradeId, () ->
                tradeService.cancel(tradeId, req.seller()));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get an open trade proposal by id")
    @GetMapping("/{tradeId}")
    public ResponseEntity<TradeProposal> getProposal(@PathVariable String tradeId) {
        return ResponseEntity.ok(tradeService.getProposal(tradeId));
    }

    @Operation(summary = "Get a settled trade record by id")
    @GetMapping("/{tradeId}/executed")
    public ResponseEntity<ExecutedTrade> getExecuted(@PathVariable String tradeId) {
        return ResponseEntity.ok(tradeService.getExecutedTrade(tradeId));
    }

    @Operation(summary = "List open trade proposals visible to a party")
    @GetMapping("/party/{party}/proposals")
    public ResponseEntity<List<TradeProposal>> proposalsForParty(@PathVariable String party) {
        return ResponseEntity.ok(tradeService.getProposalsForParty(party));
    }

    @Operation(summary = "List settled trades visible to a party")
    @GetMapping("/party/{party}/executed")
    public ResponseEntity<List<ExecutedTrade>> executedForParty(@PathVariable String party) {
        return ResponseEntity.ok(tradeService.getExecutedTradesForParty(party));
    }
}
