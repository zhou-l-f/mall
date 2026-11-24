package com.macro.mall.seckill.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.seckill.domain.SeckillActivityDetail;
import com.macro.mall.seckill.domain.SeckillResult;
import com.macro.mall.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀抢购Controller
 */
@RestController
@Tag(name = "SeckillController", description = "秒杀抢购")
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @Operation(summary = "秒杀抢购")
    @PostMapping("/{activityId}/{skuId}")
    public CommonResult<SeckillResult> seckill(@PathVariable Long activityId,
                                               @PathVariable Long skuId) {
        SeckillResult result = seckillService.seckill(activityId, skuId);
        //售罄、限购等业务失败按正常返回封装下发，与全局异常处理保持同一结构
        if (SeckillResult.STATUS_FAILED.equals(result.getStatus())) {
            return CommonResult.failed(result.getReason());
        }
        return CommonResult.success(result);
    }

    @Operation(summary = "查询秒杀活动详情")
    @GetMapping("/activity/{activityId}")
    public CommonResult<SeckillActivityDetail> detail(@PathVariable Long activityId) {
        return CommonResult.success(seckillService.getActivityDetail(activityId));
    }

    @Operation(summary = "查询秒杀抢购结果")
    @GetMapping("/result/{orderSn}")
    public CommonResult<SeckillResult> result(@PathVariable String orderSn) {
        return CommonResult.success(seckillService.getResult(orderSn));
    }
}
