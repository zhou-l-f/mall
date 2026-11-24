package com.macro.mall.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.seckill.admin.domain.SeckillActivity;
import com.macro.mall.seckill.admin.domain.SeckillSku;
import com.macro.mall.service.SeckillAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀活动与商品后台配置Controller
 */
@Controller
@Tag(name = "SeckillAdminController", description = "秒杀活动与商品配置")
@RequestMapping("/seckill")
public class SeckillAdminController {

    @Autowired
    private SeckillAdminService seckillAdminService;

    @Operation(summary = "创建秒杀活动")
    @RequestMapping(value = "/activity/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult createActivity(@RequestBody SeckillActivity activity) {
        int count = seckillAdminService.createActivity(activity);
        return count > 0 ? CommonResult.success(count) : CommonResult.failed();
    }

    @Operation(summary = "编辑秒杀活动")
    @RequestMapping(value = "/activity/update/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateActivity(@PathVariable Long id, @RequestBody SeckillActivity activity) {
        int count = seckillAdminService.updateActivity(id, activity);
        return count > 0 ? CommonResult.success(count) : CommonResult.failed();
    }

    @Operation(summary = "分页查询秒杀活动")
    @RequestMapping(value = "/activity/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<SeckillActivity>> listActivities(@RequestParam(required = false) String keyword,
                                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        List<SeckillActivity> list = seckillAdminService.listActivities(keyword, pageNum, pageSize);
        return CommonResult.success(CommonPage.restPage(list));
    }

    @Operation(summary = "修改活动状态")
    @RequestMapping(value = "/activity/status/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateActivityStatus(@PathVariable Long id, @RequestParam Integer status) {
        int count = seckillAdminService.updateActivityStatus(id, status);
        return count > 0 ? CommonResult.success(count) : CommonResult.failed();
    }

    @Operation(summary = "创建秒杀商品")
    @RequestMapping(value = "/sku/create", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult createSku(@RequestBody SeckillSku sku) {
        int count = seckillAdminService.createSku(sku);
        return count > 0 ? CommonResult.success(count) : CommonResult.failed();
    }

    @Operation(summary = "编辑秒杀商品")
    @RequestMapping(value = "/sku/update/{id}", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult updateSku(@PathVariable Long id, @RequestBody SeckillSku sku) {
        int count = seckillAdminService.updateSku(id, sku);
        return count > 0 ? CommonResult.success(count) : CommonResult.failed();
    }

    @Operation(summary = "查询活动下的秒杀商品")
    @RequestMapping(value = "/sku/list", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<SeckillSku>> listSkus(@RequestParam Long activityId) {
        List<SeckillSku> list = seckillAdminService.listSkus(activityId);
        return CommonResult.success(list);
    }
}
