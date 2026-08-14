package org.mannerth.starxcar.order.rating;

import org.mannerth.starxcar.constant.UserType;
import org.mannerth.starxcar.order.rating.request.RatingSubmitRequest;
import org.mannerth.starxcar.order.rating.response.OrderRatingResponse;
import org.mannerth.starxcar.order.rating.response.RatingPageResponse;

public interface RatingService {

    /** 乘客对已完成订单的司机提交 1-5 星评分；一个订单只能评价一次。 */
    OrderRatingResponse submit(Long passengerId, String orderNo, RatingSubmitRequest request);

    /** 按调用者角色读取订单评价：乘客仅限本人订单，司机仅限自己接的订单，管理员可读任意订单。 */
    OrderRatingResponse getOrderRating(Long requesterId, UserType requesterType, String orderNo);

    /** 分页查询司机收到的评价，按提交时间倒序。 */
    RatingPageResponse listDriverRatings(Long driverId, long page, long size);

    /** 分页查询乘客提交的评价，按提交时间倒序。 */
    RatingPageResponse listPassengerRatings(Long passengerId, long page, long size);
}