import type { Metadata } from "next";
import { TripConcepts } from "./trip-concepts";

export const metadata: Metadata = {
  title: "GoJapan · 旅行计划与手账",
  description: "在一处规划行程、查看地点，并用照片和艺术文字记录旅途。",
};

export default function Home() {
  return <TripConcepts />;
}
