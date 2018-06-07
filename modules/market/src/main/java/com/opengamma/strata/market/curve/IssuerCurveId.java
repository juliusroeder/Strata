/*
 * Copyright (C) 2018 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.market.curve;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;

import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaBean;
import org.joda.beans.TypedMetaBean;
import org.joda.beans.gen.BeanDefinition;
import org.joda.beans.gen.PropertyDefinition;
import org.joda.beans.impl.light.LightMetaBean;

import com.opengamma.strata.data.MarketDataName;
import com.opengamma.strata.data.NamedMarketDataId;
import com.opengamma.strata.data.ObservableSource;

/**
 * An identifier used to access an issuer curve by name.
 * <p>
 * This is used when there is a need to obtain an issuer curve as an instance of {@link Curve}.
 */
@BeanDefinition(style = "light", cacheHashCode = true)
public final class IssuerCurveId
    implements NamedMarketDataId<Curve>, ImmutableBean, Serializable {

  /**
   * The curve group name.
   */
  @PropertyDefinition(validate = "notNull")
  private final CurveGroupName curveGroupName;
  /**
   * The curve name.
   */
  @PropertyDefinition(validate = "notNull")
  private final CurveName curveName;
  /**
   * The source of observable market data.
   */
  @PropertyDefinition(validate = "notNull")
  private final ObservableSource observableSource;

  //-------------------------------------------------------------------------
  /**
   * Obtains an instance used to obtain an issuer curve by name.
   *
   * @param groupName  the curve group name
   * @param curveName  the curve name
   * @return the identifier
   */
  public static IssuerCurveId of(String groupName, String curveName) {
    return new IssuerCurveId(
        CurveGroupName.of(groupName),
        CurveName.of(curveName),
        ObservableSource.NONE);
  }

  /**
   * Obtains an instance used to obtain am issuer curve by name.
   *
   * @param groupName  the curve group name
   * @param curveName  the curve name
   * @return the identifier
   */
  public static IssuerCurveId of(CurveGroupName groupName, CurveName curveName) {
    return new IssuerCurveId(groupName, curveName, ObservableSource.NONE);
  }

  /**
   * Obtains an instance used to obtain an issuer curve by name, 
   * specifying the source of observable market data.
   *
   * @param groupName  the curve group name
   * @param curveName  the curve name
   * @param obsSource  the source of observable market data
   * @return the identifier
   */
  public static IssuerCurveId of(
      CurveGroupName groupName,
      CurveName curveName,
      ObservableSource obsSource) {

    return new IssuerCurveId(groupName, curveName, obsSource);
  }

  //-------------------------------------------------------------------------
  @Override
  public Class<Curve> getMarketDataType() {
    return Curve.class;
  }

  @Override
  public MarketDataName<Curve> getMarketDataName() {
    return curveName;
  }

  @Override
  public String toString() {
    return new StringBuilder(32)
        .append("IssuerCurveId:")
        .append(curveGroupName)
        .append('/')
        .append(curveName)
        .append(observableSource.equals(ObservableSource.NONE) ? "" : "/" + observableSource)
        .toString();
  }

  //------------------------- AUTOGENERATED START -------------------------
  /**
   * The meta-bean for {@code IssuerCurveId}.
   */
  private static final TypedMetaBean<IssuerCurveId> META_BEAN =
      LightMetaBean.of(
          IssuerCurveId.class,
          MethodHandles.lookup(),
          new String[] {
              "curveGroupName",
              "curveName",
              "observableSource"},
          new Object[0]);

  /**
   * The meta-bean for {@code IssuerCurveId}.
   * @return the meta-bean, not null
   */
  public static TypedMetaBean<IssuerCurveId> meta() {
    return META_BEAN;
  }

  static {
    MetaBean.register(META_BEAN);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The cached hash code, using the racy single-check idiom.
   */
  private transient int cacheHashCode;

  private IssuerCurveId(
      CurveGroupName curveGroupName,
      CurveName curveName,
      ObservableSource observableSource) {
    JodaBeanUtils.notNull(curveGroupName, "curveGroupName");
    JodaBeanUtils.notNull(curveName, "curveName");
    JodaBeanUtils.notNull(observableSource, "observableSource");
    this.curveGroupName = curveGroupName;
    this.curveName = curveName;
    this.observableSource = observableSource;
  }

  @Override
  public TypedMetaBean<IssuerCurveId> metaBean() {
    return META_BEAN;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the curve group name.
   * @return the value of the property, not null
   */
  public CurveGroupName getCurveGroupName() {
    return curveGroupName;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the curve name.
   * @return the value of the property, not null
   */
  public CurveName getCurveName() {
    return curveName;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the source of observable market data.
   * @return the value of the property, not null
   */
  public ObservableSource getObservableSource() {
    return observableSource;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      IssuerCurveId other = (IssuerCurveId) obj;
      return JodaBeanUtils.equal(curveGroupName, other.curveGroupName) &&
          JodaBeanUtils.equal(curveName, other.curveName) &&
          JodaBeanUtils.equal(observableSource, other.observableSource);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = cacheHashCode;
    if (hash == 0) {
      hash = getClass().hashCode();
      hash = hash * 31 + JodaBeanUtils.hashCode(curveGroupName);
      hash = hash * 31 + JodaBeanUtils.hashCode(curveName);
      hash = hash * 31 + JodaBeanUtils.hashCode(observableSource);
      cacheHashCode = hash;
    }
    return hash;
  }

  //-------------------------- AUTOGENERATED END --------------------------

}
