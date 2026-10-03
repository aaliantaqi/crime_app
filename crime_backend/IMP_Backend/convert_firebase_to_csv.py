"""
Convert Firebase reports.json to training CSV with combined risk labels.

Risk now combines THREE components, so every one of the five inputs the
app collects (city, area, time_of_day, day_of_week, crime_type) genuinely
affects the label the model is trained on:

  A) Time-based risk       (time_of_day + day_of_week)
  B) Frequency-based risk  (report count per city+area+time_of_day —
                             this is how city/area actually enter the
                             formula, via how "busy" that specific
                             city+area+time combination has been)
  C) Crime-type risk       (an inherent severity weight per crime type)

  Combined:  (time * 0.30) + (frequency * 0.40) + (crime_type * 0.30)

Previously, crime type was collected by the app and passed all the way
through to the backend, but the label formula never looked at it — every
crime type at the same city+area+time+day was silently forced to the
same risk label. Component (C) below fixes that.
"""

import datetime
import json
from pathlib import Path

import pandas as pd
from sklearn.utils import resample

SCRIPT_DIR = Path(__file__).resolve().parent
INPUT_FILE = SCRIPT_DIR / "reports.json"
OUTPUT_FILE = SCRIPT_DIR / "data" / "sample_crime_data.csv"

VALID_CRIME_TYPES = {"Theft", "Assault", "Fraud", "Vandalism"}
WEEKEND_DAYS = {"Friday", "Saturday"}
RISK_SCORES = {"High": 3, "Medium": 2, "Low": 1}

TIME_WEIGHT = 0.30
FREQ_WEIGHT = 0.40
CRIME_WEIGHT = 0.30

# ----------------------------------------------------------------------
# Inherent risk weight per crime type.
#
# This is a design assumption, not something measured from data: violent
# crime (Assault) is treated as inherently higher-risk than non-violent /
# property crime, which is a standard, widely used distinction in
# criminology and crime-statistics reporting. Theft and Fraud are treated
# as comparable mid-level risks, and Vandalism (property damage, no
# direct confrontation) as the lowest of the four.
#
# This is a reasonable, defensible default for an FYP, but it's worth
# stating explicitly in your report as an assumption rather than an
# empirically derived weighting, since the project's own report data
# doesn't currently include severity/outcome information to derive it
# from directly.
# ----------------------------------------------------------------------
CRIME_TYPE_RISK = {
    "Assault": "High",
    "Theft": "Medium",
    "Fraud": "Medium",
    "Vandalism": "Low",
}

# ----------------------------------------------------------------------
# REAL-DATA-ONLY SWITCH
#
# When True (default): only your actual logged reports are used. No
# synthetic/made-up rows are added for missing time+day combinations, no
# synthetic "quiet" exemplar rows are added, and no rows are duplicated to
# balance class counts. The output CSV will have exactly one row per
# valid real report (each with a risk label computed by the formula
# above) — nothing more.
#
# When False: restores the original behavior — missing time/day/area
# combinations get filled in with synthetic rows, low-frequency exemplars
# are added, and minority risk classes get oversampled up to a capped
# ratio. This produces a larger, more "complete" training set, at the
# cost of it no longer being 100% real reported data.
#
# Note: train_model.py still applies its own (separate, capped, leak-free)
# oversampling during training regardless of this switch — that happens
# downstream and only affects the training split, not this master CSV.
# ----------------------------------------------------------------------
USE_REAL_DATA_ONLY = True

# Cap on how far minority-class oversampling is allowed to go (only
# applies when USE_REAL_DATA_ONLY is False).
MAX_OVERSAMPLE_RATIO = 3.0


def normalize_crime_type(raw: str) -> str:
    if not raw:
        return ""
    r = raw.strip().lower()
    if "theft" in r:
        return "Theft"
    if "assault" in r:
        return "Assault"
    if "fraud" in r:
        return "Fraud"
    if "vandalism" in r:
        return "Vandalism"
    return ""


def get_time_of_day(timestamp_ms) -> str:
    if not timestamp_ms:
        return "Morning"
    dt = datetime.datetime.fromtimestamp(timestamp_ms / 1000)
    hour = dt.hour
    if 5 <= hour < 12:
        return "Morning"
    if 12 <= hour < 18:
        return "Afternoon"
    return "Night"


def get_day_of_week(timestamp_ms) -> str:
    if not timestamp_ms:
        return "Monday"
    dt = datetime.datetime.fromtimestamp(timestamp_ms / 1000)
    return dt.strftime("%A")


def time_based_risk(time_of_day: str, day_of_week: str) -> str:
    """A) Time-based risk from time slot and weekday."""
    if time_of_day == "Morning":
        return "Low"
    if time_of_day == "Afternoon":
        return "Medium"
    if time_of_day == "Night":
        if day_of_week in WEEKEND_DAYS:
            return "High"
        return "Medium"
    return "Low"


def frequency_based_risk(report_count: int) -> str:
    """B) Frequency-based risk from city+area+time_of_day report count."""
    if report_count > 20:
        return "High"
    if report_count >= 10:
        return "Medium"
    return "Low"


def crime_type_risk(crime_type: str) -> str:
    """C) Inherent risk weight per crime type (see CRIME_TYPE_RISK above)."""
    return CRIME_TYPE_RISK.get(crime_type, "Medium")


def combine_risk(time_risk: str, freq_risk: str, crime_risk: str) -> str:
    """Weighted combination of time, frequency, AND crime-type risk."""
    time_score = RISK_SCORES[time_risk]
    freq_score = RISK_SCORES[freq_risk]
    crime_score = RISK_SCORES[crime_risk]
    final_score = (
        (time_score * TIME_WEIGHT)
        + (freq_score * FREQ_WEIGHT)
        + (crime_score * CRIME_WEIGHT)
    )
    if final_score >= 2.5:
        return "High"
    if final_score >= 1.5:
        return "Medium"
    return "Low"


def build_frequency_map(df: pd.DataFrame) -> pd.Series:
    """Count reports per city+area+time_of_day from actual Firebase data."""
    counts = df.groupby(["city", "area", "time_of_day"]).size()
    return counts


def assign_combined_risk(
    time_of_day: str,
    day_of_week: str,
    city: str,
    area: str,
    crime_type: str,
    freq_map: pd.Series,
) -> str:
    freq_count = int(freq_map.get((city, area, time_of_day), 0))
    time_risk = time_based_risk(time_of_day, day_of_week)
    freq_risk = frequency_based_risk(freq_count)
    crime_risk = crime_type_risk(crime_type)
    return combine_risk(time_risk, freq_risk, crime_risk)


def augment_time_coverage(df: pd.DataFrame, freq_map: pd.Series) -> pd.DataFrame:
    """
    Firebase timestamps skew toward Night. Add rows for missing time/day combos
    so the model learns time-of-day and weekday patterns across all areas.
    """
    if df.empty:
        return df

    time_slots = ["Morning", "Afternoon", "Night"]
    key_days = ["Monday", "Wednesday", "Friday", "Saturday"]
    extra_rows = []

    for (city, area), group in df.groupby(["city", "area"]):
        crime_types = group["crime_type"].value_counts()
        default_crime = crime_types.index[0] if len(crime_types) else "Theft"

        existing = set(zip(group["time_of_day"], group["day_of_week"]))
        for time_of_day in time_slots:
            for day in key_days:
                if (time_of_day, day) in existing:
                    continue
                extra_rows.append(
                    {
                        "city": city,
                        "area": area,
                        "crime_type": default_crime,
                        "time_of_day": time_of_day,
                        "day_of_week": day,
                        "report_count": int(freq_map.get((city, area, time_of_day), 0)),
                        "risk_level": assign_combined_risk(
                            time_of_day, day, city, area, default_crime, freq_map
                        ),
                    }
                )

    if not extra_rows:
        return df

    augmented = pd.concat([df, pd.DataFrame(extra_rows)], ignore_index=True)
    print(f"Added {len(extra_rows)} synthetic rows for time/day coverage")
    return augmented


def augment_low_frequency_coverage(df: pd.DataFrame) -> pd.DataFrame:
    """
    Real Firebase data has no city+area+time combo with <10 reports, so Low risk
    never appears from actual counts alone. Add exemplar rows with low report_count
    so the model learns the frequency component of the combined score.
    """
    if df.empty:
        return df

    crime_default = "Theft"
    extra_rows = []
    cities = sorted(df["city"].unique())

    for city in cities:
        city_areas = sorted(df.loc[df["city"] == city, "area"].unique())
        if not city_areas:
            continue
        area = city_areas[0]
        extra_rows.append(
            {
                "city": city,
                "area": area,
                "crime_type": crime_default,
                "time_of_day": "Morning",
                "day_of_week": "Monday",
                "report_count": 5,
                "risk_level": combine_risk("Low", "Low", crime_type_risk(crime_default)),
            }
        )

    if not extra_rows:
        return df

    augmented = pd.concat([df, pd.DataFrame(extra_rows)], ignore_index=True)
    print(f"Added {len(extra_rows)} low-frequency exemplar rows for Low risk class")
    return augmented


def balance_risk_classes(df: pd.DataFrame, random_state: int = 42,
                          max_ratio: float = MAX_OVERSAMPLE_RATIO) -> pd.DataFrame:
    """
    Oversample minority risk classes so High, Medium, and Low are closer to
    balanced for training, capped at `max_ratio` times each class's own
    original size instead of unlimited duplication up to the largest
    class, to avoid manufacturing large numbers of exact duplicate rows
    that a model can simply memorize.
    """
    counts = df["risk_level"].value_counts()
    if counts.empty or len(counts) < 2:
        return df

    target = counts.max()
    balanced_parts = []
    for label in ["High", "Medium", "Low"]:
        subset = df[df["risk_level"] == label]
        if subset.empty:
            continue
        original_size = len(subset)
        capped_target = min(target, int(original_size * max_ratio))
        capped_target = max(capped_target, original_size)  # never shrink a class
        if original_size < capped_target:
            subset = resample(subset, replace=True, n_samples=capped_target, random_state=random_state)
        balanced_parts.append(subset)

    if not balanced_parts:
        return df

    balanced = pd.concat(balanced_parts, ignore_index=True)
    return balanced.sample(frac=1, random_state=random_state).reset_index(drop=True)


def main():
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    reports = data.get("reports", data)
    rows = []

    for _, report in reports.items():
        city = report.get("city") or report.get("location")
        area = report.get("area")
        crime_type = normalize_crime_type(report.get("crimeType"))
        timestamp = report.get("timestamp")

        if not city or not area or crime_type not in VALID_CRIME_TYPES:
            continue

        rows.append(
            {
                "city": city.strip(),
                "area": area.strip(),
                "crime_type": crime_type,
                "time_of_day": get_time_of_day(timestamp),
                "day_of_week": get_day_of_week(timestamp),
            }
        )

    df = pd.DataFrame(rows)
    print(f"Total valid reports loaded: {len(df)}")

    freq_map = build_frequency_map(df)
    print(f"\nFrequency map (city+area+time_of_day) - sample counts:")
    for key, count in freq_map.head(12).items():
        print(f"  {key}: {count} -> {frequency_based_risk(count)}")

    df["report_count"] = df.apply(
        lambda row: int(freq_map.get((row["city"], row["area"], row["time_of_day"]), 0)),
        axis=1,
    )
    df["risk_level"] = df.apply(
        lambda row: assign_combined_risk(
            row["time_of_day"],
            row["day_of_week"],
            row["city"],
            row["area"],
            row["crime_type"],
            freq_map,
        ),
        axis=1,
    )

    print(f"\nRisk distribution (before augmentation):\n{df['risk_level'].value_counts()}")

    if USE_REAL_DATA_ONLY:
        print("\nUSE_REAL_DATA_ONLY = True — skipping synthetic time/day rows, "
              "low-frequency exemplars, and class-balancing duplication. "
              "The CSV below reflects ONLY your real logged reports.")
    else:
        df = augment_time_coverage(df, freq_map)
        df = augment_low_frequency_coverage(df)
        df = df[df["crime_type"].isin(VALID_CRIME_TYPES)]
        print(f"\nRisk distribution (after augmentation):\n{df['risk_level'].value_counts()}")
        df = balance_risk_classes(df)
        print(f"\nRisk distribution (balanced for training, capped at {MAX_OVERSAMPLE_RATIO}x per class):\n{df['risk_level'].value_counts()}")

    print(f"\nCities: {sorted(df['city'].unique())}")
    print(f"Areas: {sorted(df['area'].unique())}")
    print(f"Crime types: {sorted(df['crime_type'].unique())}")
    print(f"\nTime of day distribution:\n{df['time_of_day'].value_counts()}")
    print(f"\nCrime type distribution:\n{df['crime_type'].value_counts()}")

    OUTPUT_FILE.parent.mkdir(exist_ok=True)
    df.to_csv(OUTPUT_FILE, index=False)
    print(f"\nSaved {len(df)} rows to {OUTPUT_FILE}")


if __name__ == "__main__":
    main()