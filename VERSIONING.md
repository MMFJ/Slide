# MMFJ Slide Versioning

This fork uses **4-part versioning**: `MAJOR.MINOR.PATCH.BUILD`

## Format

- **MAJOR.MINOR.PATCH**: Matches upstream [ccrama/Slide](https://github.com/ccrama/Slide) version
- **BUILD**: Incremented for each MMFJ-specific release (matches `versionCode`)

## Example

`7.3.8.742`
- Based on upstream Slide **7.3.8**
- Build **742** of MMFJ fork

## Workflow

1. When pulling upstream changes, keep `MAJOR.MINOR.PATCH` aligned with upstream
2. Increment `BUILD` (versionCode) for each MMFJ release
3. Tag releases as `MAJOR.MINOR.PATCH.BUILD`

## Repository Structure

- **origin**: `https://github.com/MMFJ/Slide.git` (MMFJ fork)
- **upstream**: `https://github.com/ccrama/Slide.git` (original repo)

To sync with upstream:
```bash
git fetch upstream
git merge upstream/master
# Resolve conflicts if any
git push origin mmfj-mod
```
