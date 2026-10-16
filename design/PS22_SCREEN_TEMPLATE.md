# PS 22 reusable screen design template

Use this brief when creating a single screen concept with an image model, a designer, or a prototype tool. A template is a reusable set of instructions and named slots: keep the fixed PS 22 visual rules, replace the bracketed fields for each screen, then attach one PNG reference so the tool can extract composition cues. The reference guides layout and mood; do not copy its logo, text, or distinctive artwork.

## How to reuse

1. Copy the prompt below into your image/design tool.
2. Replace every bracketed field. Give one screen and one main task per run.
3. Attach the PNG reference. State what to borrow (for example, whitespace or crop) and what must remain original.
4. Generate a first pass, then check the screen at the supplied viewport and at 200% text size. Revise crowded areas, contrast, and touch targets.
5. For a related screen, keep the visual-system paragraph and change role, task, screen, data, state, and viewport.

## Copy-and-fill prompt

Create an original native Android screen concept for **PS 22**, a football league and tournament manager. This is one screen in a coherent mobile product, not a poster or marketing page.

**Role:** [organiser / assigned team manager / spectator]  
**Task:** [one thing the user needs to complete]  
**Screen:** [screen name]  
**Viewport:** [device and dp or pixel dimensions; include compact/expanded if relevant]  
**Data:** [realistic names, scores, dates, statuses; never use lorem ipsum]  
**State:** [loading / populated / empty / offline / error / success]  
**Reference image:** [attached PNG; describe the composition cue to borrow]

Visual direction: near-black charcoal foundation (#080B0F), layered charcoal surfaces, crisp warm-white type, and a restrained lime action accent (#B8F36A). Strong condensed athletic headline paired with highly readable sans-serif controls and dense data. Spacious editorial hierarchy, aligned to an 8 dp rhythm. Use purposeful football imagery only when it clarifies context; preserve negative space and keep information legible. Cards have restrained 12–24 dp corners, hairline separators, and subtle elevation. Avoid glassmorphism, gratuitous gradients, neon overload, tiny labels, decorative stats, and copied brand assets.

Interaction and accessibility: show the primary action and its enabled/disabled state; make navigation and controls at least 48 dp; include visible focus/pressed feedback; communicate status with text and icon/color; support large text and screen readers. Motion is brief and purposeful (roughly 150–250 ms), with a reduced-motion alternative. Keep the main task usable one-handed on the compact viewport and reflow content rather than stretch it on expanded screens.

Deliver one polished screen with realistic copy, hierarchy, spacing, component states, and a short annotation of the key interaction. Do not invent unavailable live data or claim an external action succeeded.

## Filled example

**Role:** spectator  
**Task:** check the next kickoff and current score  
**Screen:** matchday home  
**Viewport:** 393 × 852 dp Android phone  
**Data:** Harbor City (HBR) vs Northline Athletic (NTH), 1–0, second half, North Stand Arena  
**State:** live and connected  
**Reference image:** supplied Saved Pictures set; borrow spacious title hierarchy and a single strong crop, keep PS 22 colors and original team marks.

Use the copy-and-fill prompt's visual and interaction rules. Put the live score first, match phase and venue directly below, and the next fixture after it. Keep standings and navigation one tap away. Do not add a betting, subscription, or fantasy-sports action.
