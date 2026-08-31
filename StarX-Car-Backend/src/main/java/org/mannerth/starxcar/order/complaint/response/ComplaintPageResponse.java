package org.mannerth.starxcar.order.complaint.response;

import java.util.List;

/** A paginated complaint work queue. */
public record ComplaintPageResponse(long page, long size, long total, List<ComplaintResponse> records) {}
