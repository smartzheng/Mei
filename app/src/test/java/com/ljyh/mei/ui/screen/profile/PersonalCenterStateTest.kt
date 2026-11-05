package com.ljyh.mei.ui.screen.profile

import com.ljyh.mei.data.model.response.UserVipInfo
import com.ljyh.mei.data.network.Resource
import org.junit.Assert.*
import org.junit.Test

class PersonalCenterStateTest {
    private fun benefit(expiry: Long, level: Int = 1) = UserVipInfo.Benefit(
        expireTime = expiry, vipLevel = level, iconUrl = null, dynamicIconUrl = null,
    )
    private fun vip(svip: UserVipInfo.Benefit? = null, regular: UserVipInfo.Benefit? = null,
        family: UserVipInfo.Benefit? = null) = Resource.Success(UserVipInfo("", 200, UserVipInfo.Data(
        redVipLevelIcon = null, redVipLevel = 0, redVipAnnualCount = 0, associator = regular,
        musicPackage = null, familyVip = family, redplus = svip, redVipDynamicIconUrl = null, redVipDynamicIconUrl2 = null,
    )))

    @Test fun loadingFailureAndMissingDataDoNotClaimUserHasNoMembership() {
        assertEquals("同步中", profileMembership(Resource.Loading, 100).status)
        assertEquals("暂时无法获取", profileMembership(Resource.Error("offline"), 100).status)
        assertEquals("暂无数据", profileMembership(Resource.Success(UserVipInfo("", 200, null)), 100).status)
        assertEquals("暂时无法获取", profileMembership(Resource.Success(UserVipInfo("", 500, null)), 100).status)
    }

    @Test fun activeMembershipTakesPrecedenceOverExpiredBenefit() {
        assertEquals(ProfileMembership("VIP", "有效", 200), profileMembership(vip(svip = benefit(99), regular = benefit(200)), 100))
    }

    @Test fun expiryAtCurrentTimeIsExpiredEvenIfLevelIsZero() {
        assertEquals(ProfileMembership("VIP", "已过期", 100), profileMembership(vip(regular = benefit(100, 0)), 100))
    }

    @Test fun familyMembershipAndSvipPriorityAreSupported() {
        assertEquals("家庭会员", profileMembership(vip(family = benefit(200)), 100).label)
        assertEquals("SVIP", profileMembership(vip(svip = benefit(200), family = benefit(200)), 100).label)
    }

    @Test fun inactiveFutureBenefitIsNotShownAsValid() {
        assertNotEquals("有效", profileMembership(vip(regular = benefit(200, 0)), 100).status)
    }
}
