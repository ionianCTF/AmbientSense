import { PageFrame } from "../components/PageFrame";
import { Overview } from "../components/Overview";
import { Strands } from "../components/Strands";
import { FieldBanner } from "../components/FieldBanner";
import { Method } from "../components/Method";
import { Epochs } from "../components/Epochs";
import { Screenshots } from "../components/Screenshots";
import { RangeExplorer } from "../components/RangeExplorer";
import { Evaluation } from "../components/Evaluation";
import { MeshStrip } from "../components/MeshStrip";
import { Club } from "../components/Club";
import { Uses, ConclusionSection } from "../components/Conclusion";
import type { Path } from "../router";

export function IntroductionPage() {
  return (
    <PageFrame path="/introduction">
      <Overview />
      <FieldBanner />
      <Strands />
    </PageFrame>
  );
}

export function MethodPage() {
  return (
    <PageFrame path="/method">
      <Method />
      <Epochs />
    </PageFrame>
  );
}

export function ScreensPage() {
  return (
    <PageFrame path="/screens">
      <Screenshots />
    </PageFrame>
  );
}

export function RangingPage() {
  return (
    <PageFrame path="/ranging">
      <RangeExplorer />
    </PageFrame>
  );
}

export function EvaluationPage() {
  return (
    <PageFrame path="/evaluation">
      <Evaluation />
      <MeshStrip />
    </PageFrame>
  );
}

export function CommunityPage() {
  return (
    <PageFrame path="/community">
      <Club />
    </PageFrame>
  );
}

export function ConclusionPage() {
  return (
    <PageFrame path="/conclusion">
      <Uses />
      <ConclusionSection />
    </PageFrame>
  );
}

export type { Path };
