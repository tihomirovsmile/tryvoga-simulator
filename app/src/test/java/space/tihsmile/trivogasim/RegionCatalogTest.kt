package space.tihsmile.trivogasim

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import space.tihsmile.trivogasim.data.Region
import space.tihsmile.trivogasim.data.RegionCatalog
import space.tihsmile.trivogasim.data.Subscription

class RegionCatalogTest {
    @Before fun prepare() {
        RegionCatalog.installForUnitTests(listOf(
            Region("obl_poltava", "Полтавська область", "OBLAST"),
            Region("ray_poltava", "Полтавський район", "RAYON", "obl_poltava"),
            Region("hr_kremenchuk", "Кременчуцька громада", "HROMADA", "ray_poltava"),
            Region("city_kremenchuk", "м. Кременчук", "SETTLEMENT", "hr_kremenchuk"),
            Region("city_poltava", "м. Полтава", "SETTLEMENT", "ray_poltava"),
            Region("obl_kirov", "Кіровоградська область", "OBLAST")
        ))
    }
    @Test fun oblastSubscriptionIncludesSettlement() {
        assertTrue(RegionCatalog.matches("city_kremenchuk", "obl_poltava"))
    }
    @Test fun differentPlacesAreDistinct() {
        assertFalse(RegionCatalog.matches("city_kremenchuk", "city_poltava"))
        assertFalse(RegionCatalog.matches("city_kremenchuk", "obl_kirov"))
    }
    @Test fun onlySettlementForNonAirCategories() {
        assertFalse(RegionCatalog.isSettlement("obl_poltava"))
        assertFalse(RegionCatalog.isSettlement("hr_kremenchuk"))
        assertTrue(RegionCatalog.isSettlement("city_kremenchuk"))
    }
    @Test fun airCanTargetDistrict() {
        assertEquals("RAYON", RegionCatalog.type("ray_poltava"))
        assertEquals("OBLAST", RegionCatalog.type("obl_poltava"))
    }
    @Test fun threatsNotDeliveredToProvinceOnly() {
        val province = listOf(Subscription("obl_poltava"))
        assertNull(RegionCatalog.deliverySubscription("city_kremenchuk", province, true))
        assertEquals("obl_poltava", RegionCatalog.deliverySubscription("city_kremenchuk", province, false))
    }
    @Test fun threatsDeliveredToExplicitCityFollow() {
        val city = listOf(Subscription("obl_poltava"), Subscription("city_kremenchuk"))
        assertEquals("city_kremenchuk", RegionCatalog.deliverySubscription("city_kremenchuk", city, true))
    }
    @Test fun grammarOfOblastAndRayon() {
        assertEquals("у Полтавській області", RegionCatalog.locationCase("obl_poltava"))
        assertEquals("у Полтавському районі", RegionCatalog.locationCase("ray_poltava"))
    }
}
