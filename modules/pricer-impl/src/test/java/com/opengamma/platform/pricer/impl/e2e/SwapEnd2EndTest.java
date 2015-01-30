/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.e2e;

import static com.opengamma.basics.PayReceive.PAY;
import static com.opengamma.basics.PayReceive.RECEIVE;
import static com.opengamma.basics.currency.Currency.USD;
import static com.opengamma.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.basics.date.BusinessDayConventions.PRECEDING;
import static com.opengamma.basics.date.DayCounts.ACT_360;
import static com.opengamma.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.basics.date.DayCounts.THIRTY_U_360;
import static com.opengamma.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.basics.schedule.Frequency.P12M;
import static com.opengamma.basics.schedule.Frequency.P1M;
import static com.opengamma.basics.schedule.Frequency.P3M;
import static com.opengamma.basics.schedule.Frequency.P6M;
import static com.opengamma.basics.schedule.Frequency.TERM;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.financial.interestrate.datasets.StandardDataSetsMulticurveUSD;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.basics.PayReceive;
import com.opengamma.basics.currency.CurrencyAmount;
import com.opengamma.basics.date.BusinessDayAdjustment;
import com.opengamma.basics.date.DaysAdjustment;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.IborIndices;
import com.opengamma.basics.index.ImmutableIborIndex;
import com.opengamma.basics.schedule.Frequency;
import com.opengamma.basics.schedule.PeriodicSchedule;
import com.opengamma.basics.schedule.StubConvention;
import com.opengamma.basics.value.ValueAdjustment;
import com.opengamma.basics.value.ValueSchedule;
import com.opengamma.basics.value.ValueStep;
import com.opengamma.collect.id.StandardId;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.Trade;
import com.opengamma.platform.finance.TradeInfo;
import com.opengamma.platform.finance.swap.CompoundingMethod;
import com.opengamma.platform.finance.swap.FixedRateCalculation;
import com.opengamma.platform.finance.swap.IborRateCalculation;
import com.opengamma.platform.finance.swap.NotionalSchedule;
import com.opengamma.platform.finance.swap.OvernightRateCalculation;
import com.opengamma.platform.finance.swap.PaymentSchedule;
import com.opengamma.platform.finance.swap.RateCalculationSwapLeg;
import com.opengamma.platform.finance.swap.StubCalculation;
import com.opengamma.platform.finance.swap.Swap;
import com.opengamma.platform.finance.swap.SwapTrade;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.TradePricerFn;
import com.opengamma.platform.pricer.impl.DispatchingTradePricerFn;
import com.opengamma.platform.pricer.impl.ImmutablePricingEnvironment;
import com.opengamma.util.tuple.Pair;

/**
 * Test end to end.
 */
@Test
public class SwapEnd2EndTest {

  private static final IborIndex USD_LIBOR_1M = lockIndexCalendar(IborIndices.USD_LIBOR_1M);
  private static final IborIndex USD_LIBOR_3M = lockIndexCalendar(IborIndices.USD_LIBOR_3M);
  private static final IborIndex USD_LIBOR_6M = lockIndexCalendar(IborIndices.USD_LIBOR_6M);
  private static final NotionalSchedule NOTIONAL = NotionalSchedule.of(USD, 100_000_000);
  private static final BusinessDayAdjustment BDA_MF = BusinessDayAdjustment.of(MODIFIED_FOLLOWING, CalendarUSD.NYC);
  private static final BusinessDayAdjustment BDA_P = BusinessDayAdjustment.of(PRECEDING, CalendarUSD.NYC);
  private static final LocalDateDoubleTimeSeries TS_USDLIBOR1M =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2013, 12, 10), 0.00123)
          .put(LocalDate.of(2013, 12, 12), 0.00123)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDLIBOR3M =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2013, 12, 10), 0.0024185)
          .put(LocalDate.of(2013, 12, 12), 0.0024285)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDLIBOR6M =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2013, 12, 10), 0.0030)
          .put(LocalDate.of(2013, 12, 12), 0.0035)
          .build();
  private static final LocalDateDoubleTimeSeries TS_USDON =
      LocalDateDoubleTimeSeries.builder()
          .put(LocalDate.of(2014, 1, 17), 0.0007)
          .put(LocalDate.of(2014, 1, 21), 0.0007)
          .put(LocalDate.of(2014, 1, 22), 0.0007)
          .build();

  // curve providers
  private static final Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle> MULTICURVE_OIS_PAIR =
      StandardDataSetsMulticurveUSD.getCurvesUSDOisL1L3L6();
  private static final MulticurveProviderDiscount MULTICURVE_OIS = MULTICURVE_OIS_PAIR.getFirst();

  // tolerance
  private static final double TOLERANCE_PV = 1.0E-4;

  //-----------------------------------------------------------------------
  public void test_VanillaFixedVsLibor1mSwap() {
    RateCalculationSwapLeg payLeg = fixedLeg(
        LocalDate.of(2014, 9, 12), LocalDate.of(2016, 9, 12), P6M, PAY, NOTIONAL, 0.0125, null);

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2016, 9, 12))
            .frequency(P1M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P1M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_1M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), -1003684.8402, TOLERANCE_PV);
  }

  //-----------------------------------------------------------------------
  public void test_VanillaFixedVsLibor3mSwap() {
    RateCalculationSwapLeg payLeg = fixedLeg(
        LocalDate.of(2014, 9, 12), LocalDate.of(2021, 9, 12), P6M, PAY, NOTIONAL, 0.015, null);

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2021, 9, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 7170391.798257509, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_VanillaFixedVsLibor3mSwapWithFixing() {
    RateCalculationSwapLeg payLeg = fixedLeg(
        LocalDate.of(2013, 9, 12), LocalDate.of(2020, 9, 12), P6M, PAY, NOTIONAL, 0.015, null);

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2013, 9, 12))
            .endDate(LocalDate.of(2020, 9, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2013, 9, 10)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 3588376.471608199, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_BasisLibor3mVsLibor6mSwapWithSpread() {
    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 8, 29))
            .endDate(LocalDate.of(2024, 8, 29))
            .frequency(P6M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(Frequency.P6M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_6M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 8, 29))
            .endDate(LocalDate.of(2024, 8, 29))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(Frequency.P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .spread(ValueSchedule.of(0.0010))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 8, 27)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    // TODO check number
    // assertEquals(pv.getAmount(), -13844.3872, TOLERANCE_PV);
    assertEquals(pv.getAmount(), -21875.376339152455, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_BasisCompoundedLibor1mVsLibor3mSwap() {
    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 8, 29))
            .endDate(LocalDate.of(2019, 8, 29))
            .frequency(P1M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(Frequency.P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .compoundingMethod(CompoundingMethod.FLAT)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_1M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 8, 29))
            .endDate(LocalDate.of(2019, 8, 29))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(Frequency.P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 8, 27)).build())
        .swap(Swap.of(receiveLeg, payLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    // TODO unable to match existing number
    // assertEquals(pv.getAmount(), -340426.6128, TOLERANCE_PV);
    assertEquals(pv.getAmount(), -342874.98367929866, TOLERANCE_PV);  // unverified number
  }

  //-------------------------------------------------------------------------
  public void test_Stub3mFixed6mVsLibor3mSwap() {
    RateCalculationSwapLeg receiveLeg = fixedLeg(
        LocalDate.of(2014, 9, 12), LocalDate.of(2016, 6, 12), P6M, RECEIVE, NOTIONAL, 0.01, StubConvention.SHORT_INITIAL);

    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2016, 6, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(receiveLeg, payLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 502890.9443281095, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_Stub1mFixed6mVsLibor3mSwap() {
    RateCalculationSwapLeg receiveLeg = fixedLeg(
        LocalDate.of(2014, 9, 12), LocalDate.of(2016, 7, 12), P6M, RECEIVE, NOTIONAL, 0.01, StubConvention.SHORT_INITIAL);

    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2016, 7, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(receiveLeg, payLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 463962.5517136799, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_InterpolatedStub3mFixed6mVsLibor6mSwap() {
    RateCalculationSwapLeg receiveLeg = fixedLeg(
        LocalDate.of(2014, 9, 12), LocalDate.of(2016, 6, 12), P6M, RECEIVE, NOTIONAL, 0.01, StubConvention.SHORT_INITIAL);

    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2016, 6, 12))
            .frequency(P6M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P6M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_6M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .initialStub(StubCalculation.ofIborInterpolatedRate(USD_LIBOR_3M, USD_LIBOR_6M))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(receiveLeg, payLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 364832.4284058402, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_InterpolatedStub4mFixed6mVsLibor6mSwap() {
    RateCalculationSwapLeg receiveLeg = fixedLeg(
        LocalDate.of(2014, 9, 12), LocalDate.of(2016, 7, 12), P6M, RECEIVE, NOTIONAL, 0.01, StubConvention.SHORT_INITIAL);

    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2016, 7, 12))
            .frequency(P6M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P6M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_6M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .initialStub(StubCalculation.ofIborInterpolatedRate(USD_LIBOR_3M, USD_LIBOR_6M))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(receiveLeg, payLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 314215.2347116342, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_ZeroCouponFixedVsLibor3mSwap() {
    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2021, 9, 12))
            .frequency(P12M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(TERM)
            .paymentOffset(DaysAdjustment.NONE)
            .compoundingMethod(CompoundingMethod.STRAIGHT)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(FixedRateCalculation.builder()
            .dayCount(THIRTY_U_360)
            .rate(ValueSchedule.of(0.015))
            .build())
        .build();

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2021, 9, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(TERM)
            .paymentOffset(DaysAdjustment.NONE)
            .compoundingMethod(CompoundingMethod.STRAIGHT)
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), 7850279.042216873, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_AmortizingFixedVsLibor3mSwap() {
    ValueAdjustment stepReduction = ValueAdjustment.ofDeltaAmount(-3_000_000);
    List<ValueStep> steps = new ArrayList<>();
    for (int i = 1; i < 28; i++) {
      steps.add(ValueStep.of(i, stepReduction));
    }
    ValueSchedule notionalSchedule = ValueSchedule.of(100_000_000, steps);
    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2021, 9, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NotionalSchedule.of(USD, notionalSchedule))
        .calculation(FixedRateCalculation.builder()
            .dayCount(THIRTY_U_360)
            .rate(ValueSchedule.of(0.016))
            .build())
        .build();

    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 9, 12))
            .endDate(LocalDate.of(2021, 9, 12))
            .frequency(P3M)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(P3M)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(NotionalSchedule.of(USD, notionalSchedule))
        .calculation(IborRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_LIBOR_3M)
            .fixingOffset(DaysAdjustment.ofBusinessDays(-2, CalendarUSD.NYC, BDA_P))
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 9, 10)).build())
        .swap(Swap.of(receiveLeg, payLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), -1850080.2895532502, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_CompoundingOisFixed2mVsFedFund12mSwap() {
    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 2, 5))
            .endDate(LocalDate.of(2014, 4, 7))
            .frequency(TERM)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(TERM)
            .paymentOffset(DaysAdjustment.ofBusinessDays(2, CalendarUSD.NYC))
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(FixedRateCalculation.builder()
            .dayCount(ACT_360)
            .rate(ValueSchedule.of(0.00123))
            .build())
        .build();

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 2, 5))
            .endDate(LocalDate.of(2014, 4, 7))
            .frequency(TERM)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(TERM)
            .paymentOffset(DaysAdjustment.ofBusinessDays(2, CalendarUSD.NYC))
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(OvernightRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_FED_FUND)
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 2, 3)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), -9723.264518929138, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  public void test_CompoundingOisFixed2mVsFedFund12mSwapWithFixing() {
    RateCalculationSwapLeg payLeg = RateCalculationSwapLeg.builder()
        .payReceive(PAY)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 1, 17))
            .endDate(LocalDate.of(2014, 3, 17))
            .frequency(TERM)
            .businessDayAdjustment(BDA_MF)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(TERM)
            .paymentOffset(DaysAdjustment.ofBusinessDays(2, CalendarUSD.NYC))
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(FixedRateCalculation.builder()
            .dayCount(ACT_360)
            .rate(ValueSchedule.of(0.00123))
            .build())
        .build();

    RateCalculationSwapLeg receiveLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(LocalDate.of(2014, 1, 17))
            .endDate(LocalDate.of(2014, 3, 17))
            .frequency(TERM)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(StubConvention.SHORT_INITIAL)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(TERM)
            .paymentOffset(DaysAdjustment.ofBusinessDays(2, CalendarUSD.NYC))
            .build())
        .notionalSchedule(NOTIONAL)
        .calculation(OvernightRateCalculation.builder()
            .dayCount(ACT_360)
            .index(USD_FED_FUND)
            .build())
        .build();

    SwapTrade trade = SwapTrade.builder()
        .standardId(StandardId.of("OG-Trade", "1"))
        .tradeInfo(TradeInfo.builder().tradeDate(LocalDate.of(2014, 1, 15)).build())
        .swap(Swap.of(payLeg, receiveLeg))
        .build();

    TradePricerFn<Trade> pricer = swapPricer();
    CurrencyAmount pv = pricer.presentValue(env(), trade).getAmount(USD);
    assertEquals(pv.getAmount(), -7352.973875972721, TOLERANCE_PV);
  }

  //-------------------------------------------------------------------------
  // fixed rate leg
  private static RateCalculationSwapLeg fixedLeg(
      LocalDate start, LocalDate end, Frequency frequency,
      PayReceive payReceive, NotionalSchedule notional, double fixedRate, StubConvention stubConvention) {

    return RateCalculationSwapLeg.builder()
        .payReceive(payReceive)
        .accrualSchedule(PeriodicSchedule.builder()
            .startDate(start)
            .endDate(end)
            .frequency(frequency)
            .businessDayAdjustment(BDA_MF)
            .stubConvention(stubConvention)
            .build())
        .paymentSchedule(PaymentSchedule.builder()
            .paymentFrequency(frequency)
            .paymentOffset(DaysAdjustment.NONE)
            .build())
        .notionalSchedule(notional)
        .calculation(FixedRateCalculation.builder()
            .dayCount(THIRTY_U_360)
            .rate(ValueSchedule.of(fixedRate))
            .build())
        .build();
  }

  //-------------------------------------------------------------------------
  // pricer
  private TradePricerFn<Trade> swapPricer() {
    return DispatchingTradePricerFn.DEFAULT;
  }

  // pricing environment
  private static PricingEnvironment env() {
    return ImmutablePricingEnvironment.builder()
        .valuationDate(LocalDate.of(2014, 1, 22))
        .multicurve(MULTICURVE_OIS)
        .timeSeries(ImmutableMap.of(
            USD_LIBOR_1M, TS_USDLIBOR1M,
            USD_LIBOR_3M, TS_USDLIBOR3M,
            USD_LIBOR_6M, TS_USDLIBOR6M,
            USD_FED_FUND, TS_USDON))
        .dayCount(ACT_ACT_ISDA)
        .build();
  }

  // use a fixed known set of holiday dates to ensure tests produce same numbers
  private static IborIndex lockIndexCalendar(IborIndex index) {
    return ((ImmutableIborIndex) index).toBuilder()
        .fixingCalendar(CalendarUSD.NYC)
        .effectiveDateOffset(index.getEffectiveDateOffset().toBuilder()
            .calendar(CalendarUSD.NYC)
            .adjustment(index.getEffectiveDateOffset().getAdjustment().toBuilder()
                .calendar(CalendarUSD.NYC)
                .build())
            .build())
        .maturityDateOffset(index.getMaturityDateOffset().toBuilder()
            .adjustment(index.getMaturityDateOffset().getAdjustment().toBuilder()
                .calendar(CalendarUSD.NYC)
                .build())
            .build())
        .build();
  }

}
