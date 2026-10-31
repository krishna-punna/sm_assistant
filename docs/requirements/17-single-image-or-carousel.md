# 17. One image per post, or a carousel when the content needs it

Source: Krishna, project thread, 2026-10-31. Plan: [single-image-carousel.md](../design/single-image-carousel.md).

> "Now has we improved the Image building capabilities, i want o stick with only one image generation.
> Application should have intelligence that the given Content can be build in One Image
> Or It has to develop and build the content into multiple Images. Because Long Images may not get attracted by users,
> Or Users May not show that much intreset."

> "If Image is legnth to build and design in vertical/potrait you can try horizontal as Landscape"

> "let move to implementation" (approval of the plan, 2026-10-31)

| ID | Requirement |
|----|-------------|
| SC-1 | Generate Image (and Content & Image, and scheduled image posts) makes one result per request, not 4 picks. |
| SC-2 | The app decides from the content whether it fits one image or must be split over several images (a carousel). |
| SC-3 | Content too long for a portrait image is tried as a landscape image before it is split. |
| SC-4 | A carousel is posted to LinkedIn so readers can swipe through it. |
